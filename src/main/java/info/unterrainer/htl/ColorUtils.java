package info.unterrainer.htl;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ColorUtils {

	private static final Map<String, Color> NAMED = mkNamed();

	private static Map<String, Color> mkNamed() {
		// Minimal map covering your palette (tweak if you like)
		Map<String, Color> m = new HashMap<>();
		m.put("pink", new Color(255, 192, 203));
		m.put("lightblue", new Color(173, 216, 230));
		m.put("violet", new Color(238, 130, 238));
		m.put("lightyellow", new Color(255, 255, 224));
		m.put("plum", new Color(221, 160, 221));
		m.put("salmon", new Color(250, 128, 114));
		m.put("lightgreen", new Color(144, 238, 144));
		m.put("blue", new Color(0, 0, 255));
		m.put("ivory", new Color(255, 255, 240));
		m.put("red", new Color(255, 0, 0));
		m.put("mediumvioletred", new Color(199, 21, 133));
		m.put("orangered", new Color(255, 69, 0));
		m.put("darkorange", new Color(255, 140, 0));
		m.put("orange", new Color(255, 165, 0));
		m.put("gold", new Color(255, 215, 0));
		m.put("khaki", new Color(240, 230, 140));
		m.put("thistle", new Color(216, 191, 216));
		m.put("mediumslateblue", new Color(123, 104, 238));
		m.put("palegreen", new Color(152, 251, 152));
		return m;
	}

    public static List<String> getNamedColors() {
        return NAMED.keySet().stream().sorted().collect(Collectors.toList());
    }

    public static String pickRandomBaseColor() {
        var keys = getNamedColors();
        return keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
    }

	public static String pickStampColor(String baseColorHex) {
		Color base = parseColor(baseColorHex);
		float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
		float h = hsb[0], s = hsb[1], b = hsb[2];

		// Determine if the petal color is "too yellowish"
		// Hue for yellow ≈ 50–65° (0.14–0.18), lightness high
		if (h > 0.12f && h < 0.20f && b > 0.8f) {
			// Move hue toward orange (~30°, 0.08)
			float targetHue = 0.08f + randInRange(-0.02f, 0.02f);
			float newHue = lerp(h, targetHue, 0.7f); // 70% toward orange
			float newSat = clamp01(s + randInRange(-0.1f, 0.1f));
			float newBri = clamp01(b - 0.15f + randInRange(-0.05f, 0.05f));

			int rgb = Color.HSBtoRGB(newHue, newSat, newBri);
			return toHex(rgb);
		}

		// Otherwise, pick a slightly darker / more saturated version of the base
		float newSat = clamp01(s + randInRange(-0.05f, 0.1f));
		float newBri = clamp01(b - 0.1f + randInRange(-0.05f, 0.05f));
		int rgb = Color.HSBtoRGB(h, newSat, newBri);
		return toHex(rgb);
	}

	private static float randInRange(float min, float max) {
		return (float) (min + Math.random() * (max - min));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static Color parseColor(String hex) {
		return Color.decode(hex);
	}

	/**
	 * Build a list of hex colors (#RRGGBB), one per petal, each a slight variation
	 * around the given base color name.
	 */
	public static List<String> generatePetalColors(String baseName, int petals) {
		Color base = NAMED.getOrDefault(baseName.toLowerCase(Locale.ROOT), Color.PINK);

		// Convert base to HSB
		float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
		float h = hsb[0], s = hsb[1], b = hsb[2];

		ThreadLocalRandom rnd = ThreadLocalRandom.current();

		// Reasonable jitter ranges; adjust to taste
		float hueJitter = 8f / 360f; // ±8°
		float satJitter = 0.08f; // ±0.08
		float briJitter = 0.08f; // ±0.08

		return IntStream.range(0, petals).mapToObj(i -> {
			float h2 = wrap01(h + randInRange(rnd, -hueJitter, hueJitter));
			float s2 = clamp01(s + randInRange(rnd, -satJitter, satJitter));
			float b2 = clamp01(b + randInRange(rnd, -briJitter, briJitter));
			int rgb = Color.HSBtoRGB(h2, s2, b2);
			return toHex(rgb);
		}).collect(Collectors.toList());
	}

	private static float randInRange(ThreadLocalRandom rnd, float min, float max) {
		return min + rnd.nextFloat() * (max - min);
	}

	private static float clamp01(float v) {
		if (v < 0f)
			return 0f;
		if (v > 1f)
			return 1f;
		return v;
	}

	private static float wrap01(float v) {
		// wrap hue to [0,1)
		v = v % 1f;
		if (v < 0f)
			v += 1f;
		return v;
	}

	private static String toHex(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = (argb) & 0xFF;
		return String.format("#%02X%02X%02X", r, g, b);
	}
}
