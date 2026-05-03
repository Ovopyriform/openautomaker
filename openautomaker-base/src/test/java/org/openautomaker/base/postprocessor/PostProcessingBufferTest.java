package org.openautomaker.base.postprocessor;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openautomaker.base.postprocessor.events.GCodeParseEvent;

//TODO: Test case needs filling out.  How to test?
@ExtendWith(MockitoExtension.class)
public class PostProcessingBufferTest {

	/**
	 * Test of emptyBufferToOutput method, of class PostProcessingBuffer.
	 */
	@Test
	public void testEmptyBufferToOutput() throws Exception {
		System.out.println("emptyBufferToOutput");
		GCodeOutputWriter outputWriter = mock(GCodeOutputWriter.class);
		PostProcessingBuffer instance = new PostProcessingBuffer();
		instance.emptyBufferToOutput(outputWriter);
	}

	/**
	 * Test of closeNozzle method, of class PostProcessingBuffer.
	 */
	@Test
	public void testCloseNozzle() {
		System.out.println("closeNozzle");
		String comment = "";
		GCodeOutputWriter outputWriter = mock(GCodeOutputWriter.class);
		PostProcessingBuffer instance = new PostProcessingBuffer();
		instance.closeNozzle(comment, outputWriter);
	}

	/**
	 * Test of openNozzleFullyBeforeExtrusion method, of class PostProcessingBuffer.
	 */
	@Test
	public void testOpenNozzleFullyBeforeExtrusion() {
		System.out.println("openNozzleFullyBeforeExtrusion");
		PostProcessingBuffer instance = new PostProcessingBuffer();
		instance.openNozzleFullyBeforeExtrusion();
	}

	/**
	 * Test of add method, of class PostProcessingBuffer.
	 */
	@Test
	public void testAdd() {
		System.out.println("add");
		GCodeParseEvent e = null;
		PostProcessingBuffer instance = new PostProcessingBuffer();
		boolean expResult = false;
		boolean result = instance.add(e);
	}

	/**
	 * Test of clear method, of class PostProcessingBuffer.
	 */
	@Test
	public void testClear() {
		System.out.println("clear");
		PostProcessingBuffer instance = new PostProcessingBuffer();
		instance.clear();
	}

}
