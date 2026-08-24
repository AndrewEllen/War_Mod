package com.andye.warmod.acoustics.model;

/**
 * Material-independent response of a sound class to the sampled world geometry.
 * Large pressure waves retain more low-frequency energy through terrain and foliage
 * and produce stronger, longer-range reflections than small firearm reports.
 */
public enum AcousticResponseProfile {
	STANDARD(0.58, 0.22, 0.050, 0.014, 0.24, 1.00, 0.24, 2, false),
	FIREARM(0.54, 0.18, 0.045, 0.012, 0.28, 1.00, 0.22, 2, true),
	IMPACT(0.60, 0.20, 0.052, 0.014, 0.24, 1.10, 0.26, 2, false),
	EXPLOSION(0.24, 0.07, 0.018, 0.005, 0.56, 1.62, 0.48, 3, true);

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
