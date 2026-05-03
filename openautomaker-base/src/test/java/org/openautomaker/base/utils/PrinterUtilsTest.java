package org.openautomaker.base.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openautomaker.base.configuration.BaseConfiguration;
import org.openautomaker.base.configuration.Filament;
import org.openautomaker.base.configuration.datafileaccessors.FilamentContainer;
import org.openautomaker.base.printerControl.model.Head;
import org.openautomaker.base.printerControl.model.NozzleHeater;
import org.openautomaker.base.printerControl.model.Printer;
import org.openautomaker.test_library.GuiceExtension;

import jakarta.inject.Inject;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

//TODO: Check the commented elements of this test.  Always commented, may be something useful
@ExtendWith(GuiceExtension.class)
public class PrinterUtilsTest {

	@Inject
	FilamentContainer filamentContainer;

	@Inject
	PrinterUtils printerUtils;

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

		ObservableMap<Integer, Filament> effectiveFilaments = FXCollections.observableHashMap();
		effectiveFilaments.put(0, filament);

		NozzleHeater nozzleHeater0 = new NozzleHeater();

		Head mockHead = mock(Head.class);
		when(mockHead.headTypeProperty()).thenReturn(new SimpleObjectProperty<>(Head.HeadType.SINGLE_MATERIAL_HEAD));
		when(mockHead.getNozzleHeaters()).thenReturn(FXCollections.observableArrayList(nozzleHeater0));

		Printer printer = mock(Printer.class);
		when(printer.effectiveFilamentsProperty()).thenReturn(effectiveFilaments);
		when(printer.headProperty()).thenReturn(new SimpleObjectProperty<>(mockHead));

		nozzleHeater0.lastFilamentTemperatureProperty().set(NOZZLE_TEMP - BaseConfiguration.maxPermittedTempDifferenceForPurge + 1);

		List<Boolean> usedExtruders = new ArrayList<>();
		usedExtruders.add(0, true);
		usedExtruders.add(1, false);

		boolean purgeIsNecessary = printerUtils.isPurgeNecessary(printer, usedExtruders);
		assertFalse(purgeIsNecessary);

		nozzleHeater0.lastFilamentTemperatureProperty().set(NOZZLE_TEMP - BaseConfiguration.maxPermittedTempDifferenceForPurge - 1);
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

		ObservableMap<Integer, Filament> effectiveFilaments = FXCollections.observableHashMap();
		effectiveFilaments.put(0, filament0);
		effectiveFilaments.put(1, filament1);

		NozzleHeater nozzleHeater0 = new NozzleHeater();
		NozzleHeater nozzleHeater1 = new NozzleHeater();

		Head mockHead = mock(Head.class);
		when(mockHead.headTypeProperty()).thenReturn(new SimpleObjectProperty<>(Head.HeadType.DUAL_MATERIAL_HEAD));
		when(mockHead.getNozzleHeaters()).thenReturn(FXCollections.observableArrayList(nozzleHeater0, nozzleHeater1));

		Printer printer = mock(Printer.class);
		when(printer.effectiveFilamentsProperty()).thenReturn(effectiveFilaments);
		when(printer.headProperty()).thenReturn(new SimpleObjectProperty<>(mockHead));

		nozzleHeater0.lastFilamentTemperatureProperty().set(NOZZLE_TEMP_0 - BaseConfiguration.maxPermittedTempDifferenceForPurge + 1);
		nozzleHeater1.lastFilamentTemperatureProperty().set(NOZZLE_TEMP_1 - BaseConfiguration.maxPermittedTempDifferenceForPurge + 1);

		List<Boolean> usedExtruders = new ArrayList<>();
		usedExtruders.add(0, true);
		usedExtruders.add(1, false);

		// Looks like it's going down the wrong path because it's not detecting the dual head for some reason
		boolean purgeIsNecessary = printerUtils.isPurgeNecessary(printer, usedExtruders);
		assertFalse(purgeIsNecessary);

		nozzleHeater1.lastFilamentTemperatureProperty().set(NOZZLE_TEMP_1 - BaseConfiguration.maxPermittedTempDifferenceForPurge - 1);
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
