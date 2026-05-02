package celtech.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openautomaker.base.configuration.fileRepresentation.CameraProfile;

public class CameraProfileStringConverterTest {
	@Test
	public void testStringConverter() {
		List<CameraProfile> l = new ArrayList<>();
		l.add(new CameraProfile("Default", 1080, 1920, true, true, false, 0, 0, null, null));
		l.add(new CameraProfile("Logitech 920", 1080, 1920, true, true, false, 0, 0, "Default", null));
		l.add(new CameraProfile("Logitech StreamCam", 1080, 1920, true, true, false, 0, 0, "Default", null));

		CameraProfileStringConverter cisc = new CameraProfileStringConverter(() -> {
			return l;
		});

		String cs0 = cisc.toString(l.get(0));
		CameraProfile cp1 = cisc.fromString("Logitech 920");
		assertEquals(cs0, "Default");
		assertEquals(cp1, l.get(1));
	}
}
