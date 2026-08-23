package com.andye.warmod.acoustics.model;

/**
 * Material-independent response of a sound class to the sampled world geometry.
 * Large pressure waves retain more low-frequency energy through terrain and foliage
 * and produce stronger, longer-range reflections than small firearm reports.
 */
public enum AcousticResponseProfile {
	STANDARD(0.72, 0.38, 0.075, 0.035, 0.12, 1.00, 0.24, 2, false),
	FIREARM(0.68, 0.30, 0.070, 0.028, 0.16, 1.00, 0.24, 2, false),
	EXPLOSION(0.38, 0.10, 0.030, 0.010, 0.34, 1.75, 0.46, 3, true);

	private final double obstructionAbsorption;
	private final double foliageAbsorption;
	private final double obstructionPitchDamping;
	private final double foliagePitchDamping;
	private final double minimumTransmissionGain;
	private final double reflectionGain;
	private final double maximumEchoVolumeRatio;
	private final int maximumEchoes;
	private final boolean distantTerrainReflections;

	AcousticResponseProfile(final double obstructionAbsorption,
		final double foliageAbsorption, final double obstructionPitchDamping,
		final double foliagePitchDamping, final double minimumTransmissionGain,
		final double reflectionGain, final double maximumEchoVolumeRatio,
		final int maximumEchoes, final boolean distantTerrainReflections) {
		this.obstructionAbsorption = obstructionAbsorption;
		this.foliageAbsorption = foliageAbsorption;
		this.obstructionPitchDamping = obstructionPitchDamping;
		this.foliagePitchDamping = foliagePitchDamping;
		this.minimumTransmissionGain = minimumTransmissionGain;
		this.reflectionGain = reflectionGain;
		this.maximumEchoVolumeRatio = maximumEchoVolumeRatio;
		this.maximumEchoes = maximumEchoes;
		this.distantTerrainReflections = distantTerrainReflections;
	}

	public double obstructionAbsorption() { return obstructionAbsorption; }
	public double foliageAbsorption() { return foliageAbsorption; }
	public double obstructionPitchDamping() { return obstructionPitchDamping; }
	public double foliagePitchDamping() { return foliagePitchDamping; }
	public double minimumTransmissionGain() { return minimumTransmissionGain; }
	public double reflectionGain() { return reflectionGain; }
	public double maximumEchoVolumeRatio() { return maximumEchoVolumeRatio; }
	public int maximumEchoes() { return maximumEchoes; }
	public boolean distantTerrainReflections() { return distantTerrainReflections; }
}
