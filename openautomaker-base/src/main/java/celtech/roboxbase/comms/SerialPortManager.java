package celtech.roboxbase.comms;

import java.io.UnsupportedEncodingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fazecast.jSerialComm.SerialPort;

import celtech.roboxbase.comms.exceptions.PortNotFoundException;
import celtech.roboxbase.comms.remote.LowLevelInterfaceException;

/**
 *
 * @author Ian
 */
public class SerialPortManager
{

    private String serialPortToConnectTo = null;
    protected SerialPort serialPort = null;
	private static final Logger LOGGER = LogManager.getLogger();

    // ROB-453: timeout needed when firmware is out of date and status report is too short
    private final static int READ_TIMEOUT = 5000;

    public SerialPortManager(String portToConnectTo)
    {
        this.serialPortToConnectTo = portToConnectTo;
    }

    public boolean connect(int baudrate) throws PortNotFoundException
    {
        boolean portSetupOK = false;

		LOGGER.debug("About to open serial port " + serialPortToConnectTo);
        serialPort = SerialPort.getCommPort(serialPortToConnectTo);

        try
        {
            serialPort.openPort();

            serialPort.setComPortParameters(baudrate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            serialPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
            serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT, 0);

            portSetupOK = true;
			LOGGER.debug("Finished opening serial port " + serialPortToConnectTo);
        } catch (Exception ex)
        {
			LOGGER.error("Error setting up serial port " + ex.getMessage());
        }

        return portSetupOK;
    }

    public void disconnect() throws LowLevelInterfaceException
    {
		LOGGER.debug("Disconnecting port " + serialPortToConnectTo);

        checkSerialPortOK();

        if (serialPort != null)
        {
            try
            {
                serialPort.closePort();
				LOGGER.debug("Port " + serialPortToConnectTo + " disconnected");
            } catch (Exception ex)
            {
				LOGGER.error("Error closing serial port");
            }
            serialPort = null;
        }
    }

    public boolean writeBytes(byte[] data) throws LowLevelInterfaceException
    {
        boolean wroteOK = false;

        try
        {
            checkSerialPortOK();
            int nWritten = serialPort.writeBytes(data, data.length);
            wroteOK = (nWritten == data.length);
        } catch (Exception ex)
        {
            throw new LowLevelInterfaceException(ex.getMessage() + " port " + serialPort.getSystemPortName());
        }
        return wroteOK;
    }

    // Write bytes and verify all were sent. The subsequent readSerialPort() call blocks until
    // response data arrives (TIMEOUT_READ_SEMI_BLOCKING with READ_TIMEOUT covers no-response case).
    public void writeAndWaitForData(byte[] data) throws LowLevelInterfaceException
    {
        checkSerialPortOK();
        boolean wroteOK = writeBytes(data);

        if (!wroteOK)
        {
            String message = "";
            if (serialPort != null)
            {
                message += serialPort.getSystemPortName() + " ";
            }
            message += "Failure during write";
            throw new LowLevelInterfaceException(message);
        }
    }

    public byte[] readSerialPort(int numBytes) throws LowLevelInterfaceException
    {
        checkSerialPortOK();

        byte[] returnData = new byte[numBytes];
        try
        {
            serialPort.readBytes(returnData, numBytes);
        } catch (Exception ex)
        {
            throw new LowLevelInterfaceException(serialPort.getSystemPortName()
                    + " Check availability - Printer did not respond in time");
        }
        return returnData;
    }

    public byte[] readAllDataOnBuffer() throws LowLevelInterfaceException
    {
        checkSerialPortOK();
        byte[] buffer = new byte[serialPort.bytesAvailable()];
        try
        {
            serialPort.readBytes(buffer, buffer.length);
            return buffer;
        } catch (Exception ex)
        {
            throw new LowLevelInterfaceException(ex.getMessage());
        }
    }

    public boolean writeASCIIString(String string) throws LowLevelInterfaceException
    {
        checkSerialPortOK();

        try
        {
            byte[] buffer = string.getBytes("US-ASCII");
            int nWritten = serialPort.writeBytes(buffer, buffer.length);
            return (nWritten == buffer.length);
        } catch (UnsupportedEncodingException ex)
        {
			LOGGER.error("Strange error with encoding");
            ex.printStackTrace();
            throw new LowLevelInterfaceException(serialPortToConnectTo
                    + " Encoding error whilst writing ASCII string");
        } catch (Exception ex)
        {
            throw new LowLevelInterfaceException(ex.getMessage());
        }
    }

    public String readString() throws LowLevelInterfaceException
    {
        checkSerialPortOK();

        try
        {
            byte[] buffer = readAllDataOnBuffer();
            return new String(buffer);
        } catch (Exception ex)
        {
            throw new LowLevelInterfaceException(ex.getMessage());
        }
    }

    private void checkSerialPortOK() throws LowLevelInterfaceException
    {
        if (serialPort == null)
        {
            throw new LowLevelInterfaceException(serialPortToConnectTo
                    + " Serial port not open");
        }
    }
}
