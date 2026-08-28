package net.azib.ipscan.config;

import net.azib.ipscan.fetchers.Fetcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.prefs.Preferences;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GUIConfigTest {

	private Preferences preferences;
	private GUIConfig config;

	@Before
	public void setUp() throws Exception {
		preferences = Preferences.userRoot().node("ipscan-test");
		preferences.clear();
		config = new GUIConfig(preferences);
	}

	@After
	public void tearDown() throws Exception {
		preferences.removeNode();
	}

	@Test
	public void setMainWindowDimensions() throws Exception {
		config.mainWindowSize = new int[] {2, 1};
		config.isMainWindowMaximized = false;
		assertFalse(config.isMainWindowMaximized);
		assertArrayEquals(new int[] {2, 1}, config.mainWindowSize);

		config.mainWindowSize = new int[] {3, 4};
		config.isMainWindowMaximized = true;
		assertTrue(config.isMainWindowMaximized);
		assertArrayEquals(new int[] {3, 4}, config.mainWindowSize);
	}

	@Test
	public void store() throws Exception {
		config.mainWindowSize = new int[] {33, 44};
		config.isMainWindowMaximized = false;
		config.store();
		assertEquals(33, preferences.getInt("windowWidth", 0));

		config.mainWindowSize = new int[] {55, 66};
		config.isMainWindowMaximized = true;
		config.store();
		assertEquals(33, preferences.getInt("windowWidth", 0));
	}

	@Test
	public void columnWidths() throws Exception {
		var fetcher = mock(Fetcher.class);
		when(fetcher.getId()).thenReturn("fetcher.abc");

		config.setColumnWidth(fetcher, 35);
		assertEquals(35, config.getColumnWidth(fetcher));
		assertEquals(35, preferences.getInt("columnWidth." + fetcher.getId(), 0));
	}
}
