/*
  This file is a part of Angry IP Scanner source code,
  see http://www.angryip.org/ for more information.
  Licensed under GPLv2.
 */
package net.azib.ipscan;

import javafx.application.Application;
import net.azib.ipscan.config.*;
import net.azib.ipscan.di.Injector;
import net.azib.ipscan.gui.fx.FXGUI;
import net.azib.ipscan.util.GoogleAnalytics;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.logging.Logger;

import static net.azib.ipscan.config.Labels.getLabel;

/**
 * The main executable class.
 * It initializes all the needed stuff and launches the user interface.
 * <p/>
 * All Exceptions, which are thrown out of the program, are caught and logged
 * using the java.util.logging facilities.
 * 
 * @see #main(String...)
 * @author Anton Keks
 */
public class Main {
	static final Logger LOG = LoggerFactory.getLogger();

	/**
	 * The launcher
	 */
	public static void main(String... args) {
		try {
			var startTime = System.currentTimeMillis();
			disableDNSCache();

			var locale = Config.getConfig().getLocale();
			Labels.initialize(locale);
			LOG.finer("Labels and Config initialized after " + (System.currentTimeMillis() - startTime));

			var injector = new ComponentRegistry().init();
			LOG.finer("Components initialized after " + (System.currentTimeMillis() - startTime));

			processCommandLine(args, injector);

			// Launch JavaFX GUI
			FXGUI.init(injector, args.length == 0);
			Application.launch(FXGUI.class, args);

			Config.getConfig().store();
		}
		catch (UnsatisfiedLinkError e) {
			e.printStackTrace();
			new GoogleAnalytics().report(e);
			showFallbackError("Failed to load native code for Java " +
					System.getProperty("java.runtime.version") + " on " + System.getProperty("os.arch") +
					"\nProbably you are using a binary built for wrong OS or CPU.\n\n" + e.getMessage());

			if (Platform.MAC_OS) {
				try {
					Files.walk(Path.of(System.getProperty("user.home"), ".swt", "lib")).map(Path::toFile).forEach(File::delete);
				} catch (Exception ignore) {}
			}
		}
		catch (Throwable e) {
			e.printStackTrace();
			new GoogleAnalytics().report(e);
			showFallbackError(e.toString() + "\nPlease submit a bug report mentioning your OS and what exactly were you doing.");
		}
	}

	private static void showFallbackError(String message) {
		try {
			System.err.println(message);
			try { Files.writeString(Path.of(System.getProperty("user.home"), ".swt", "ipscan-crash.txt"), message); } catch (Exception ignore) {}
			if (Platform.MAC_OS)
				Runtime.getRuntime().exec(new String[] {"osascript", "-e", "display notification \"" + message + "\" with title \"Angry IP Scanner\""});
			else
				Class.forName("javax.swing.JOptionPane").getMethod("showMessageDialog", Class.forName("java.awt.Component"), Object.class)
					.invoke(null, null, message);
		}
		catch (Exception e) {
			System.err.println(e);
		}
	}

	private static void disableDNSCache() {
		Security.setProperty("networkaddress.cache.ttl", "0");
		Security.setProperty("networkaddress.cache.negative.ttl", "0");
	}

	private static void processCommandLine(String[] args, Injector injector) {
		if (args.length != 0) {
			var cli = injector.require(CommandLineProcessor.class);
			try {
				cli.parse(args);
			}
			catch (Exception e) {
				showMessageToConsole(e.getMessage() + "\n\n" + cli);
				System.exit(1);
			}
		}
	}

	private static void showMessageToConsole(String usageText) {
		System.err.println(usageText);
	}
}
