package org.openautomaker.root;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.openautomaker.environment.OpenAutomakerEnv.OPENAUTOMAKER;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class RootUUIDTest {

	private static final String AUTOMAKER_ROOT_UUID = OPENAUTOMAKER + ".root.uuid";
	private static final Preferences PREFS = Preferences.userRoot().node(OPENAUTOMAKER);

	@AfterEach
	public void tearDown() {
		PREFS.remove(AUTOMAKER_ROOT_UUID);
	}

	@Test
	public void testRootUUID() {
		String actual = RootUUID.get();
		String stored = PREFS.get(AUTOMAKER_ROOT_UUID, null);

		assertNotNull(actual);
		assertEquals(actual, stored);
	}
}
