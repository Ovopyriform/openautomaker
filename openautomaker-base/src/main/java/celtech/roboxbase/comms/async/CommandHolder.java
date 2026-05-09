package celtech.roboxbase.comms.async;

import java.util.concurrent.CompletableFuture;

import celtech.roboxbase.comms.rx.RoboxRxPacket;

public class CommandHolder
{

	private final CommandPacket commandPacket;
	private final CompletableFuture<RoboxRxPacket> future;

	public CommandHolder(CommandPacket commandPacket, CompletableFuture<RoboxRxPacket> future)
	{
		this.commandPacket = commandPacket;
		this.future = future;
	}

	public CommandPacket getCommandPacket()
	{
		return commandPacket;
	}

	public CompletableFuture<RoboxRxPacket> getFuture()
	{
		return future;
	}
}
