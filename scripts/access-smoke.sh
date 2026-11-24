#!/usr/bin/env bash
# 路网可达性影响端到端冒烟（基于启动时播种的 NET-DEMO）：
#   初始快照缺测支路 → 桥逐时开闭与孤立/绕行 → 水深补测出新版本并标出受影响服务点
#   → 封路更正再出版本 → 版本账核验 → 确认影响图与作业迁移状态相互独立。
# 前置：应用已启动且演示数据为初始状态（重置见 scripts/reset-db.sh）。
set -euo pipefail

BASE=${BASE:-http://127.0.0.1:8080}
AUTH=${AUTH:-operator:operator123}
NETWORK_CODE=${NETWORK_CODE:-NET-DEMO}

req() { curl -sf -u "$AUTH" -H 'Content-Type: application/json' "$@"; }
step() { echo; echo "=== $* ==="; }

NID=$(req "$BASE/api/access/networks" | jq -r ".[] | select(.networkCode==\"$NETWORK_CODE\") | .id")
[ -n "$NID" ] || { echo "找不到演示路网 $NETWORK_CODE"; exit 1; }
echo "路网 ID: $NID"
HORIZON_START=$(req "$BASE/api/access/networks/$NID" | jq -r '.horizonStart')
echo "分析窗口起点: $HORIZON_START"
slot_time() { date -u -d "$HORIZON_START +$1 hours" +%Y-%m-%dT%H:%M:%SZ; }

step "1. 初始快照（v1）：缺测支路 L-BRANCH 全程 UNKNOWN，不得当畅通"
V1=$(req "$BASE/api/access/networks/$NID/snapshots/current")
echo "$V1" | jq '{versionNo, status, triggerType, unknownLinkCodes: (.unknownLinkCodesJson | fromjson)}'
echo "$V1" | jq -e '.unknownLinkCodesJson | fromjson | index("L-BRANCH") != null' >/dev/null

step "2. 时变通行图：桥 L-BRIDGE 阈值 0.35m，T2/T3（0.40/0.45m）封闭"
echo "$V1" | jq -r '.timeVaryingGraphJson' | jq -r '
  .links[] | select(.linkCode=="L-BRIDGE")
  | .slots[] | "T\(.slot): \(.state)  \(.reason)  depth=\(.depthM)"'
echo "$V1" | jq -e '
  (.timeVaryingGraphJson | fromjson | .links[] | select(.linkCode=="L-BRIDGE") | .slots
   | map(.state)) == ["OPEN","OPEN","CLOSED","CLOSED","OPEN","OPEN"]' >/dev/null

step "3. 桥封闭前后对比：消防站 T1 可达河东，T2 河东整体孤立、河西改走替代环路"
echo "$V1" | jq -r '.serviceImpactJson' | jq -r '
  .facilities[] | select(.facilityCode=="FIRE-01")
  | .slots[] | select(.slot==1 or .slot==2)
  | "T\(.slot): 可达街区数=\(.reachableCount) 孤立人口下界=\(.isolatedPopulationLowerBound) 绕行总增量=\(.totalDetourDeltaM)m"'
echo "$V1" | jq -e '
  (.serviceImpactJson | fromjson | .facilities[] | select(.facilityCode=="FIRE-01") | .slots[2]
   | .isolatedBlocks | map(.block) | index("N6")) != null' >/dev/null
echo "$V1" | jq -e '
  (.serviceImpactJson | fromjson | .facilities[] | select(.facilityCode=="FIRE-01") | .slots[2]
   | .detours[] | select(.block=="N3") | .deltaM) == 100' >/dev/null

step "4. 医院急诊入口独立 0.4m 阈值（路段本身不按水深封闭）"
echo "$V1" | jq -r '.serviceImpactJson' | jq -r '
  .facilities[] | select(.facilityCode=="HOSP-01")
  | .entryClosures[] | "入口封闭时间片 T\(.slot): \(.reason)"'
echo "$V1" | jq -e '
  (.serviceImpactJson | fromjson | .facilities[] | select(.facilityCode=="HOSP-01")
   | .entryClosures | map(.slot)) == [3]' >/dev/null

step "5. 水深补测：一个批次补齐缺测支路 L-BRANCH 六个时间片，只生成一版且缺测清零"
BATCH='{"observations":['
for i in 0 1 2 3 4 5; do
  [ "$i" -gt 0 ] && BATCH+=','
  BATCH+="{\"linkCode\":\"L-BRANCH\",\"observedAt\":\"$(slot_time "$i")\",\"depthM\":0.05}"
done
BATCH+=']}'
VNEW=$(req -X POST "$BASE/api/access/networks/$NID/depths/backfill-batch" -d "$BATCH")
echo "$VNEW" | jq '{versionNo, triggerType, unknownLinkCodes: (.unknownLinkCodesJson | fromjson),
  affected: (.affectedFacilityCodesJson | fromjson)}'
echo "$VNEW" | jq -e '.versionNo==2 and .triggerType=="DEPTH_BACKFILL"
  and ((.unknownLinkCodesJson | fromjson) | length)==0' >/dev/null
echo "$VNEW" | jq -e '.affectedFacilityCodesJson | fromjson | index("FIRE-01") != null' >/dev/null
echo "$VNEW" | jq -e '
  (.serviceImpactJson | fromjson | .facilities[] | select(.facilityCode=="FIRE-01") | .slots[1]
   | .reachableBlocks | index("N8")) != null' >/dev/null

step "6. 封路更正：T2..T3 强制封闭消防站出门通道 L-N0-N1，再出版本并标出受影响服务点"
VCORR=$(req -X POST "$BASE/api/access/networks/$NID/closures" -d "{
  \"linkCode\":\"L-N0-N1\",\"closed\":true,
  \"effectiveFrom\":\"$(slot_time 2)\",\"effectiveTo\":\"$(slot_time 3)\",
  \"reason\":\"权威渠道封路更正（冒烟）\"}")
echo "$VCORR" | jq '{versionNo, triggerType, affected: (.affectedFacilityCodesJson | fromjson)}'
echo "$VCORR" | jq -e '.triggerType=="CLOSURE_CORRECTION"
  and (.affectedFacilityCodesJson | fromjson | index("FIRE-01") != null)' >/dev/null

step "7. 版本账：历史版本置 SUPERSEDED，仅最新为 CURRENT"
req "$BASE/api/access/networks/$NID/snapshots" \
  | jq 'group_by(.status) | map({status: .[0].status, count: length})'
req "$BASE/api/access/networks/$NID/snapshots" | jq -e '[.[] | select(.status=="CURRENT")] | length==1' >/dev/null
req "$BASE/api/access/snapshots/$(echo "$VCORR" | jq -r '.id')/affected" | jq '{versionNo, affectedFacilityCodes, unknownLinkCodes}'

step "8. 影响图独立于作业迁移：迁移流 FY-MAIN 仍为原状态，未被可达性计算回写"
req "$BASE/api/streams" | jq '[.[] | {streamCode, status, rulePackageVersion}]'
req "$BASE/api/streams" | jq -e '.[] | select(.streamCode=="FY-MAIN") | .rulePackageVersion=="v1.0.0"' >/dev/null

echo
echo "ACCESS_SMOKE_OK"
