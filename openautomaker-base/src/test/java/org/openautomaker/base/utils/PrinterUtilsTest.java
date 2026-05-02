package org.openautomaker.base.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openautomaker.base.configuration.BaseConfiguration;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.configuration.fileRepresentation.HeadFile;
import org.openautomaker.base.configuration.fileRepresentation.NozzleHeaterData;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.mock.printer_control.model.MockPrinter;
import org.openautomaker.mock.printer_control.model.MockPrinterFactory;
import org.openautomaker.mock.printer_control.model.MockHead.TestNozzleHeater;
import org.openautomaker.test_library.GuiceExtension;

import jakarta.inject.Inject;

//TODO: Check the commented elements of this test.  Always commented, may be something useful
@ExtendWith(GuiceExtension.class)
public class PrinterUtilsTest {

	@Inject
	FilamentContainer filamentContainer;

	@Inject
	PrinterUtils printerUtils;

	@Inject
	MockPrinterFactory testPrinterFactory;

	/**
	 * Test of printJobIDIndicatesPrinting method, of class PrinterUtils.
	 */
	@Test
	public void testPrintJobIDIndicatesPrinting() {
		System.out.println("printJobIDIndicatesPrinting");

		String printJobID = "";
		boolean result = PrinterUtils.printJobIDIndicatesPrinting(printJobID);
		assertEquals(false, result);

		printJobID = null;
		result = PrinterUtils.printJobIDIndicatesPrinting(printJobID);
		assertEquals(false, result);

		printJobID = "5793b413812d443a";
		result = PrinterUtils.printJobIDIndicatesPrinting(printJobID);
		assertEquals(true, result);
	}

	@Test
	public void testRequiresPurgeForNozzleHeater0() {
		int NOZZLE_TEMP = 120;

		Filament filament = filamentContainer.getFilamentByID("RBX-ABS-PP156");
		filament.getNozzleTemperatureProperty().set(NOZZLE_TEMP);

		MockPrinter printer = testPrinterFactory.create(1);
		printer.overrideFilament(0, filament);
		printer.loadFilament(0);

		List<NozzleHeaterData> nozzleHeaters = new ArrayList<>();
		nozzleHeaters.add(new NozzleHeaterData(0, 0, 0));
		HeadFile headFile = new HeadFile(2, "RBX01-SM", null, null, 0.0f, nozzleHeaters, new ArrayList<>());

		printer.addHeadForHeadFile(headFile);

		TestNozzleHeater testNozzleHeater = (TestNozzleHeater) printer.getHead().getNozzleHeaters().get(0);
		testNozzleHeater.lastFilamentTemperatureProperty().set(NOZZLE_TEMP - BaseConfiguration.maxPermittedTempDifferenceForPurge + 1);

		List<Boolean> usedExtruders = new ArrayList<>();
		usedExtruders.add(0, true);
		usedExtruders.add(1, false);

		boolean purgeIsNecessary = printerUtils.isPurgeNecessary(printer, usedExtruders);
		assertFalse(purgeIsNecessary);

		testNozzleHeater.lastFilamentTemperatureProperty().set(NOZZLE_TEMP
				- BaseConfiguration.maxPermittedTempDifferenceForPurge - 1);
		purgeIsNecessary = printerUtils.isPurgeNecessary(printer, usedExtruders);
		assertTrue(purgeIsNecessary);
	}

	@Test
	public void testRequiresPurgeForNozzleHeater1() {
		int NOZZLE_TEMP_0 = 120;
		int NOZZLE_TEMP_1 = 220;

		Filament filament0 = filamentContainer.getFilamentByID("RBX-ABS-PP156");
		filament0.getNozzleTemperatureProperty().set(NOZZLE_TEMP_1);

		Filament filament1 = filamentContainer.getFilamentByID("RBX-ABS-GR499");
		filament1.getNozzleTemperatureProperty().set(NOZZLE_TEMP_0);

		MockPrinter printer = testPrinterFactory.create(2);
		List<NozzleHeaterData> nozzleHeaters2 = new ArrayList<>();
		nozzleHeaters2.add(new NozzleHeaterData(0, 0, 0));
		nozzleHeaters2.add(new NozzleHeaterData(0, 0, 0));
		HeadFile headFile = new HeadFile(2, "RBX01-DM", Head.HeadType.DUAL_MATERIAL_HEAD, null, 0.0f, nozzleHeaters2, new ArrayList<>());

		printer.addHeadForHeadFile(headFile);

		printer.overrideFilament(0, filament0);
		printer.loadFilament(0);
		printer.overrideFilament(1, filament1);
		printer.loadFilament(1);

		TestNozzleHeater testNozzleHeater0 = (TestNozzleHeater) printer.getHead().getNozzleHeaters().get(0);
		testNozzleHeater0.lastFilamentTemperatureProperty().set(NOZZLE_TEMP_0 - BaseConfiguration.maxPermittedTempDifferenceForPurge + 1);

		TestNozzleHeater testNozzleHeater1 = (TestNozzleHeater) printer.getHead().getNozzleHeaters().get(1);
		testNozzleHeater1.lastFilamentTemperatureProperty().set(NOZZLE_TEMP_1 - BaseConfiguration.maxPermittedTempDifferenceForPurge + 1);

		List<Boolean> usedExtruders = new ArrayList<>();
		usedExtruders.add(0, true);
		usedExtruders.add(1, false);

		// Looks like it's going down the wrong path because it's not detecting the dual head for some reason
		boolean purgeIsNecessary = printerUtils.isPurgeNecessary(printer, usedExtruders);
		assertFalse(purgeIsNecessary);

		testNozzleHeater1.lastFilamentTemperatureProperty().set(NOZZLE_TEMP_1 - BaseConfiguration.maxPermittedTempDifferenceForPurge - 1);
		purgeIsNecessary = printerUtils.isPurgeNecessary(printer, usedExtruders);
		assertTrue(purgeIsNecessary);
	}

	//    /**
	//     * Test of waitOnMacroFinished method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testWaitOnMacroFinished_Printer_Task()
	//    {
	//        System.out.println("waitOnMacroFinished");
	//        Printer printerToCheck = null;
	//        Task task = null;
	//        boolean expResult = false;
	//        boolean result = PrinterUtils.waitOnMacroFinished(printerToCheck, task);
	//        assertEquals(expResult, result);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//    /**
	//     * Test of waitOnMacroFinished method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testWaitOnMacroFinished_Printer_Cancellable()
	//    {
	//        System.out.println("waitOnMacroFinished");
	//        Printer printerToCheck = null;
	//        Cancellable cancellable = null;
	//        boolean expResult = false;
	//        boolean result = PrinterUtils.waitOnMacroFinished(printerToCheck, cancellable);
	//        assertEquals(expResult, result);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of waitOnBusy method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testWaitOnBusy_Printer_Task()
	//    {
	//        System.out.println("waitOnBusy");
	//        Printer printerToCheck = null;
	//        Task task = null;
	//        boolean expResult = false;
	//        boolean result = PrinterUtils.waitOnBusy(printerToCheck, task);
	//        assertEquals(expResult, result);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of waitOnBusy method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testWaitOnBusy_Printer_Cancellable()
	//    {
	//        System.out.println("waitOnBusy");
	//        Printer printerToCheck = null;
	//        Cancellable cancellable = null;
	//        boolean expResult = false;
	//        boolean result = PrinterUtils.waitOnBusy(printerToCheck, cancellable);
	//        assertEquals(expResult, result);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of isPurgeNecessary method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testIsPurgeNecessary()
	//    {
	//        System.out.println("isPurgeNecessary");
	//        Printer printer = null;
	//        PrinterUtils instance = null;
	//        boolean expResult = false;
	//        boolean result = instance.isPurgeNecessary(printer);
	//        assertEquals(expResult, result);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of offerPurgeIfNecessary method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testOfferPurgeIfNecessary()
	//    {
	//        System.out.println("offerPurgeIfNecessary");
	//        Printer printer = null;
	//        PrinterUtils instance = null;
	//        PurgeResponse expResult = null;
	//        PurgeResponse result = instance.offerPurgeIfNecessary(printer);
	//        assertEquals(expResult, result);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of waitUntilTemperatureIsReached method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testWaitUntilTemperatureIsReached_5args() throws Exception
	//    {
	//        System.out.println("waitUntilTemperatureIsReached");
	//        ReadOnlyIntegerProperty temperatureProperty = null;
	//        Task task = null;
	//        int temperature = 0;
	//        int tolerance = 0;
	//        int timeoutSec = 0;
	//        boolean expResult = false;
	//        boolean result = PrinterUtils.waitUntilTemperatureIsReached(temperatureProperty, task,
	//                                                                    temperature, tolerance,
	//                                                                    timeoutSec);
	//        assertEquals(expResult, result);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of waitUntilTemperatureIsReached method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testWaitUntilTemperatureIsReached_6args() throws Exception
	//    {
	//        System.out.println("waitUntilTemperatureIsReached");
	//        ReadOnlyIntegerProperty temperatureProperty = null;
	//        Task task = null;
	//        int temperature = 0;
	//        int tolerance = 0;
	//        int timeoutSec = 0;
	//        Cancellable cancellable = null;
	//        boolean expResult = false;
	//        boolean result = PrinterUtils.waitUntilTemperatureIsReached(temperatureProperty, task,
	//                                                                    temperature, tolerance,
	//                                                                    timeoutSec, cancellable);
	//        assertEquals(expResult, result);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of deriveNozzle1OverrunFromOffsets method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testDeriveNozzle1OverrunFromOffsets()
	//    {
	//        System.out.println("deriveNozzle1OverrunFromOffsets");
	//        float nozzle1Offset = 0.0F;
	//        float nozzle2Offset = 0.0F;
	//        float expResult = 0.0F;
	//        float result = PrinterUtils.deriveNozzle1OverrunFromOffsets(nozzle1Offset, nozzle2Offset);
	//        assertEquals(expResult, result, 0.0);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of deriveNozzle2OverrunFromOffsets method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testDeriveNozzle2OverrunFromOffsets()
	//    {
	//        System.out.println("deriveNozzle2OverrunFromOffsets");
	//        float nozzle1Offset = 0.0F;
	//        float nozzle2Offset = 0.0F;
	//        float expResult = 0.0F;
	//        float result = PrinterUtils.deriveNozzle2OverrunFromOffsets(nozzle1Offset, nozzle2Offset);
	//        assertEquals(expResult, result, 0.0);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of deriveNozzle1ZOffsetsFromOverrun method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testDeriveNozzle1ZOffsetsFromOverrun()
	//    {
	//        System.out.println("deriveNozzle1ZOffsetsFromOverrun");
	//        float nozzle1OverrunValue = 0.0F;
	//        float nozzle2OverrunValue = 0.0F;
	//        float expResult = 0.0F;
	//        float result = PrinterUtils.deriveNozzle1ZOffsetsFromOverrun(nozzle1OverrunValue,
	//                                                                     nozzle2OverrunValue);
	//        assertEquals(expResult, result, 0.0);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
	//
	//    /**
	//     * Test of deriveNozzle2ZOffsetsFromOverrun method, of class PrinterUtils.
	//     */
	//    @Test
	//    public void testDeriveNozzle2ZOffsetsFromOverrun()
	//    {
	//        System.out.println("deriveNozzle2ZOffsetsFromOverrun");
	//        float nozzle1OverrunValue = 0.0F;
	//        float nozzle2OverrunValue = 0.0F;
	//        float expResult = 0.0F;
	//        float result = PrinterUtils.deriveNozzle2ZOffsetsFromOverrun(nozzle1OverrunValue,
	//                                                                     nozzle2OverrunValue);
	//        assertEquals(expResult, result, 0.0);
	//        // TODO review the generated test code and remove the default call to fail.
	//        fail("The test case is a prototype.");
	//    }
}
