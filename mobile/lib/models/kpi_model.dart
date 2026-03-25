class KpiBreakdown {
  final double timeliness;
  final double completion;
  final double efficiency;

  KpiBreakdown({
    required this.timeliness,
    required this.completion,
    required this.efficiency,
  });

  factory KpiBreakdown.fromJson(Map<String, dynamic> json) {
    return KpiBreakdown(
      timeliness: (json['timeliness'] ?? 0).toDouble(),
      completion: (json['completion'] ?? 0).toDouble(),
      efficiency: (json['efficiency'] ?? 0).toDouble(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'timeliness': timeliness,
      'completion': completion,
      'efficiency': efficiency,
    };
  }
}

class KpiScore {
  final String id;
  final String staffId;
  final String staffName;
  final String? departmentId;
  final String period;
  final double score;
  final int rank;
  final KpiBreakdown? breakdown;

  KpiScore({
    required this.id,
    required this.staffId,
    required this.staffName,
    this.departmentId,
    required this.period,
    required this.score,
    required this.rank,
    this.breakdown,
  });

  factory KpiScore.fromJson(Map<String, dynamic> json) {
    return KpiScore(
      id: json['id']?.toString() ?? '',
      staffId: json['staffId']?.toString() ?? '',
      staffName: json['staffName'] ?? '',
      departmentId: json['departmentId']?.toString(),
      period: json['period'] ?? '',
      score: (json['score'] ?? 0).toDouble(),
      rank: json['rank'] ?? 0,
      breakdown: json['breakdown'] != null
          ? KpiBreakdown.fromJson(json['breakdown'])
          : null,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'staffId': staffId,
      'staffName': staffName,
      'departmentId': departmentId,
      'period': period,
      'score': score,
      'rank': rank,
      'breakdown': breakdown?.toJson(),
    };
  }
}
