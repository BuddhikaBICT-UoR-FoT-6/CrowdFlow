export interface TrafficSummaryDto {
  routeId: string;
  severity: number;
  timestamp: number;
}

export function toTrafficSummaryDto(doc: any): TrafficSummaryDto {
  return {
    routeId: doc.routeId,
    severity: doc.severityAvg ?? doc.severity ?? 0,
    timestamp: doc.lastAggregatedAtMs ?? doc.reportedAtMs ?? Date.now(),
  };
}
