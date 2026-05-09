package celtech.roboxbase.comms.async;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import celtech.roboxbase.comms.CommandInterface;
import celtech.roboxbase.comms.exceptions.RoboxCommsException;
import celtech.roboxbase.comms.rx.RoboxRxPacket;
import celtech.roboxbase.comms.rx.RxPacketTypeEnum;

public class AsyncWriteThread extends Thread
{
	// Must exceed the sum of connect + read timeouts in RemoteServerDetector.
	private static final int POLL_TIMEOUT_MS = 30000;

	private static final Logger LOGGER = LogManager.getLogger();

	private final BlockingQueue<CommandHolder> inboundQueue = new LinkedBlockingQueue<>();
	private final CommandInterface commandInterface;
	private volatile boolean keepRunning = true;

	// Sentinel used to unblock the consumer thread on shutdown.
	private static final CommandHolder POISON_PILL = new CommandHolder(null, null);

	public AsyncWriteThread(CommandInterface commandInterface, String ciReference)
	{
		this.commandInterface = commandInterface;
		this.setDaemon(true);
		this.setName("AsyncCommandProcessor|" + ciReference);
		this.setPriority(Thread.NORM_PRIORITY + 2);
	}

	public synchronized RoboxRxPacket sendCommand(CommandPacket command) throws RoboxCommsException
	{
		if (LOGGER.isTraceEnabled())
			LOGGER.trace("Sending command: {}", command.getCommand().getPacketType());

		CompletableFuture<RoboxRxPacket> future = new CompletableFuture<>();
		inboundQueue.add(new CommandHolder(command, future));

		try
		{
			RoboxRxPacket response = future.get(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);

			if (response == null || response.getPacketType() == RxPacketTypeEnum.NULL_PACKET)
				throw new RoboxCommsException("No response to command: " + command);

			return response;
		}
		catch (TimeoutException ex)
		{
			throw new RoboxCommsException("Timeout waiting for response to command: " + command);
		}
		catch (ExecutionException ex)
		{
			Throwable cause = ex.getCause();
			if (cause instanceof RoboxCommsException rce)
				throw rce;
			throw new RoboxCommsException("Error processing command " + command + ": " + cause.getMessage());
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
			throw new RoboxCommsException("Interrupted waiting for response");
		}
	}

	@Override
	public void run()
	{
		while (keepRunning)
		{
			try
			{
				CommandHolder holder = inboundQueue.take();
				if (holder == POISON_PILL)
					break;

				try
				{
					RoboxRxPacket response = commandInterface.writeToPrinterImpl(
							holder.getCommandPacket().getCommand(),
							holder.getCommandPacket().getDontPublish());
					holder.getFuture().complete(response);
				}
				catch (RoboxCommsException ex)
				{
					LOGGER.info("Command failed: {}", ex.getMessage());
					holder.getFuture().completeExceptionally(ex);
				}
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	public void shutdown()
	{
		keepRunning = false;
		inboundQueue.add(POISON_PILL);
	}
}
