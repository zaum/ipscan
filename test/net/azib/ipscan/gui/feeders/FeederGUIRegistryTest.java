package net.azib.ipscan.gui.feeders;

import net.azib.ipscan.feeders.Feeder;
import net.azib.ipscan.feeders.FeederException;
import net.azib.ipscan.feeders.RangeFeeder;
import org.junit.Test;

import static org.junit.Assert.*;

public class FeederGUIRegistryTest {

	@Test
	public void rangeFeederIsNeverNull() throws Exception {
		var feeder = new RangeFeeder("127.0.0.1", "127.0.0.2");
		assertNotNull(feeder);
		assertNotNull(feeder.toString());
	}

	@Test
	public void rangeFeederCreatesDifferentInstances() throws Exception {
		var feeder1 = new RangeFeeder("127.0.0.1", "127.0.0.2");
		var feeder2 = new RangeFeeder("127.0.0.1", "127.0.0.2");
		assertNotSame(feeder1, feeder2);
		assertEquals(feeder1.getId(), feeder2.getId());
	}
}
