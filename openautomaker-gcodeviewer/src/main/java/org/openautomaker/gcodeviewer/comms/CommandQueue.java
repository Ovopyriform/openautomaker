
package org.openautomaker.gcodeviewer.comms;

import static org.lwjgl.glfw.GLFW.glfwPostEmptyEvent;

import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Tony
 */
public class CommandQueue extends Thread {
	private static final Logger LOGGER = LogManager.getLogger();
	private final BlockingQueue<String> pendingCommands;

	private boolean running = false;

	public CommandQueue()
	{
		this.setDaemon(true);
		this.setName("CommandQueue");
		this.setPriority(Thread.MAX_PRIORITY);

		pendingCommands = new ArrayBlockingQueue<>(10);
	}

	public boolean commandAvailable()
	{
		return !pendingCommands.isEmpty();
	}

	public String getNextCommandFromQueue()
	{
		String command = "";
		try {
			command = pendingCommands.take();
		} catch(InterruptedException e) {
			//e.printStackTrace();
		}

		LOGGER.debug("next command = \"" + command + "\"");
		return command;
	}

	public void addCommandToQueue(String command)
	{
		LOGGER.debug("Add command = \"" + command + "\"");
		try {
			pendingCommands.put(command);
			glfwPostEmptyEvent(); // Wake up main thread.
		} catch(InterruptedException e) {
			//e.printStackTrace();
		}
	}

	@Override
	public void run()
	{
		LOGGER.debug("Command Queue running ...");
		Scanner scanner = new Scanner(System.in);
		running = true;
		while (running) {
			String inputString = scanner.nextLine();
			if (inputString.equalsIgnoreCase("q")) {
				running = false;
			}
			addCommandToQueue(inputString);
		}
		LOGGER.debug("Command Queue finished");
	}

	public void stopRunning() {
		running = false;
		// Clearing the pendingCommands queue should release the run thread, if it is blocked,
		// which should then terminate
		pendingCommands.clear();
	}
}
