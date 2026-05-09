package org.openautomaker.root;

import org.openautomaker.base.PrinterColourMap;
import org.openautomaker.base.utils.PrinterUtils;
import org.openautomaker.environment.I18N;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Self-registering singleton providing static access to shared services for non-injectable classes
 * (e.g. rootDataStructures DTOs). Injected by Guice — do not instantiate directly.
 */
@Singleton
public final class RootServices {

	private static RootServices instance;

	private final I18N i18n;
	private final PrinterColourMap printerColourMap;
	private final PrinterUtils printerUtils;

	@Inject
	public RootServices(I18N i18n, PrinterColourMap printerColourMap, PrinterUtils printerUtils) {
		this.i18n = i18n;
		this.printerColourMap = printerColourMap;
		this.printerUtils = printerUtils;
		instance = this;
	}

	public static I18N getI18N() {
		return instance.i18n;
	}

	public static PrinterColourMap getPrinterColourMap() {
		return instance.printerColourMap;
	}

	public static PrinterUtils getPrinterUtils() {
		return instance.printerUtils;
	}
}
