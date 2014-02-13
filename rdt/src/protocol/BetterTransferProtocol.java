package protocol;


import java.io.*;
import java.nio.file.Paths;

import client.*;
import client.INetworkLayerAPI.TransmissionResult;

public class BetterTransferProtocol implements IDataTransferProtocol {
	private INetworkLayerAPI networkLayer;
	private int bytesSent = 0;
	private TransferMode transferMode;
	FileInputStream inputStream;
	FileOutputStream outputStream;
	int packetno = 0;
	private byte ACK;

	
	@Override
	public void TimeoutElapsed(Object tag) {
		if(tag.equals("ACKWait")){
			networkLayer.Transmit(new Packet(new byte[] {ACK}));
		}
		
	}

	
	@Override
	public void SetNetworkLayerAPI(INetworkLayerAPI networkLayer) {
		this.networkLayer = networkLayer;
	}

	
	@Override
	public void Initialize(TransferMode transferMode) {
		this.transferMode = transferMode;

		// Send mode
		if (this.transferMode == TransferMode.Send) {
			try {
				// Open the input file
				inputStream = new FileInputStream(Paths.get("")
						.toAbsolutePath() + "/tobesent.dat");
			} catch (FileNotFoundException e) {
				throw new IllegalStateException("File not found");
			}

			// Receive mode
		} else {
			try {
				// Open the output file
				outputStream = new FileOutputStream(Paths.get("")
						.toAbsolutePath() + "/received.dat");
			} catch (FileNotFoundException e) {
				throw new IllegalStateException("File could not be created");
			}
		}
	}

	
	@Override
	public boolean Tick() {
		if (this.transferMode == TransferMode.Send) {
			// Send mode
			return SendData();
		} else {
			// Receive mode
			return ReceiveData();
		}
	}

	
	/**
	 * Handles sending of data from the input file
	 * 
	 * @return whether work has been completed
	 */
	private boolean SendData() {
		Packet receivedPacket = networkLayer.Receive();
		client.Utils.Timeout.SetTimeout(1000, this, "ACKWait");
		if(packetno == 0 || receivedPacket != null){
			packetno++;
		
		// Max packet size is 1024
		byte[] readData = new byte[1024];
		
		try {
			int readSize = inputStream.read(readData);
			if (readSize >= 0) {
				if (networkLayer.Transmit(new Packet(readData)) == TransmissionResult.Failure) {
					System.out.println("Failure transmitting");
					return true;
				}
			} else {
				// readSize == -1 means End-Of-File
				try {
					// Send empty packet, to signal transmission end. Send it a
					// bunch of times to make sure it arrives
					networkLayer.Transmit(new Packet(new byte[] {}));
					networkLayer.Transmit(new Packet(new byte[] {}));
					networkLayer.Transmit(new Packet(new byte[] {}));
					networkLayer.Transmit(new Packet(new byte[] {}));
					networkLayer.Transmit(new Packet(new byte[] {}));
		
					// Close the file
					inputStream.close();
	
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				// Return true to signal work done
				return true;
			}
	
			// Print how far along we are
			bytesSent += readSize;
	
			// Get the file size
			File file = new File(Paths.get("").toAbsolutePath()
					+ "/tobesent.dat");
	
			// Print the percentage of file transmitted
			System.out.println("Sent: "+ (int) (bytesSent * 100 / (double) file.length()) + "%");
	
		} catch (IOException e) {
			// We encountered an error while reading the file. Stop work.
			System.out.println("Error reading the file: " + e.getMessage());
			return true;
		}
		}
		// Signal that work is not completed yet
		return false;
	}

	/**
	 * Handles receiving of data packets and writing data to the output file
	 * 
	 * @return Whether work has been completed
	 */
	private boolean ReceiveData() {
		// Receive a data packet
		ACK = 000;
		Packet receivedPacket = networkLayer.Receive();
		if (receivedPacket != null) {
			if(ACK == 000){
				ACK = 001;
			}
			else{
				ACK = 000;
			}
			byte[] data = receivedPacket.GetData();
			networkLayer.Transmit(new Packet(new byte[] {ACK}));

			// If the data packet was empty, we are done
			if (data.length == 0) {
				try {
					// Close the file
					outputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}

				// Signal that work is done
				return true;
			}

			// Write the data to the output file
			try {
				outputStream.write(data, 0, data.length);
				outputStream.flush();
			} catch (IOException e) {
				System.out
						.println("Failure writing to file: " + e.getMessage());
				return true;
			}
		}

		return false;
	}

}
