import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Deterministic layered muzzle-blast and supersonic-crack source generator. */
public final class GenerateFirearmAudio {
    private static final int RATE = 48_000;
    private static final String[] WEAPONS = { "pistol", "rifle", "sniper", "bullet_crack" };
    private static final String[] PROFILES = { "near", "medium", "far", "extreme" };

    public static void main(String[] args) throws Exception {
        Path output = Path.of("tools", "audio", "work", "firearm");
        Files.createDirectories(output);
        for (int weapon = 0; weapon < WEAPONS.length; weapon++) {
            for (int profile = 0; profile < PROFILES.length; profile++) {
                float[] samples = synthesize(weapon, profile);
                writeWave(output.resolve(WEAPONS[weapon] + "_" + PROFILES[profile] + ".wav"), samples);
            }
        }
    }

    private static float[] synthesize(int weapon, int profile) {
        double duration = weapon == 3 ? 0.30 + profile * 0.10 : 0.66 + profile * 0.30;
        float[] out = new float[(int) (duration * RATE)];
        Random random = new Random(0x574152464952454CL + weapon * 137L + profile * 977L);
        double lowPass = 0.0;
        double cutoff = new double[] { 0.76, 0.43, 0.20, 0.095 }[profile];
        double bodyFrequency = new double[] { 92, 74, 58 }[Math.min(weapon, 2)];
        double power = new double[] { 0.82, 1.00, 1.18, 0.72 }[weapon];
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) RATE;
            double raw = random.nextDouble() * 2.0 - 1.0;
            lowPass += cutoff * (raw - lowPass);
            double noise = profile == 0 ? raw * 0.58 + lowPass * 0.42 : lowPass;
            double sample;
            if (weapon == 3) {
                double snap = noise * Math.exp(-t * (profile == 0 ? 105.0 : 64.0));
                double bow = Math.sin(t * Math.PI * 2.0 * 680.0) * Math.exp(-t * 74.0);
                double tail = Math.sin(t * Math.PI * 2.0 * 122.0) * Math.exp(-t * 19.0);
                sample = snap * 1.35 + bow * 0.46 + tail * 0.18;
            } else {
                double crack = noise * Math.exp(-t * (weapon == 0 ? 58.0 : 46.0));
                double body = Math.sin(t * Math.PI * 2.0 * bodyFrequency)
                    * Math.exp(-t * (weapon == 2 ? 8.2 : 11.0));
                double pressure = Math.sin(t * Math.PI * 2.0 * bodyFrequency * 0.52)
                    * Math.exp(-t * 5.8);
                double echoA = delayedNoise(t, 0.075 + profile * 0.038, noise, 15.0);
                double echoB = delayedNoise(t, 0.18 + profile * 0.070, noise, 9.0);
                sample = power * (crack * 1.15 + body * 0.52 + pressure * 0.25
                    + echoA * (0.18 + profile * 0.04) + echoB * 0.12);
            }
            double distanceGain = new double[] { 1.0, 0.88, 0.72, 0.58 }[profile];
            out[i] = (float) (Math.tanh(sample * 1.42) * distanceGain);
        }
        normalize(out, 0.92F);
        return out;
    }

    private static double delayedNoise(double time, double delay, double noise, double decay) {
        if (time < delay) return 0.0;
        return noise * Math.exp(-(time - delay) * decay);
    }

    private static void normalize(float[] values, float peak) {
        float maximum = 0.0001F;
        for (float value : values) maximum = Math.max(maximum, Math.abs(value));
        float scale = peak / maximum;
        for (int i = 0; i < values.length; i++) values[i] *= scale;
    }

    private static void writeWave(Path path, float[] samples) throws IOException {
        int bytes = samples.length * 2;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
            Files.newOutputStream(path)))) {
            ascii(out, "RIFF"); littleInt(out, 36 + bytes); ascii(out, "WAVE");
            ascii(out, "fmt "); littleInt(out, 16); littleShort(out, 1);
            littleShort(out, 1); littleInt(out, RATE); littleInt(out, RATE * 2);
            littleShort(out, 2); littleShort(out, 16); ascii(out, "data");
            littleInt(out, bytes);
            for (float sample : samples)
                littleShort(out, (short) Math.round(Math.max(-1.0F,
                    Math.min(1.0F, sample)) * 32767.0F));
        }
    }
    private static void ascii(DataOutputStream out, String value) throws IOException {
        out.writeBytes(value);
    }
    private static void littleInt(DataOutputStream out, int value) throws IOException {
        out.writeByte(value); out.writeByte(value >>> 8); out.writeByte(value >>> 16);
        out.writeByte(value >>> 24);
    }
    private static void littleShort(DataOutputStream out, int value) throws IOException {
        out.writeByte(value); out.writeByte(value >>> 8);
    }
}
