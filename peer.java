import java.net.*;
import java.util.*;
import java.io.*;


public class peer implements Runnable{
	
	int peerID;
	int neighborID;
	int fileSize;
	int pieceSize;
	int pieceNm;
	Socket skt = null;
	String hskHeader="P2PFILESHARINGPROJ"; 
	
	peerProcess pp;
	
	double speed = 0;
	boolean unchokeState = false;
	boolean interestate = false;
	
	DataInputStream inMsg = null;
	DataOutputStream outMsg = null;
	
	boolean end = false;
	
	//peer constructor
	public peer(int id, int tarid, Socket s, int fileSize, int pieceSize, int pieceNm, peerProcess pp) {
		peerID = id;
		neighborID = tarid;
		skt = s;
		this.fileSize = fileSize;
		this.pieceSize = pieceSize;
		this.pieceNm = pieceNm;
		this.pp = pp;	
	}
	
	@Override
	public void run() {
		try {
			inMsg = new DataInputStream(skt.getInputStream());
			outMsg = new DataOutputStream(skt.getOutputStream());
			
			//handshake processes
			//send handshake message
			String snhsk = hskHeader + "0000000000" + peerID;
			synchronized(outMsg) {
				outMsg.writeBytes(snhsk);
				outMsg.flush();
			}
			//pf.log.connect(neighborID);
			
			//ack handshake message
			byte[] rcv = new byte[32];
			inMsg.readFully(rcv, 0, 32);
			String rcvhsk = hskHeader + "0000000000" + neighborID;
			String check = new String(rcv); //convert received message into string
			
			if (rcvhsk.equals(check)) {
				//System.out.println("Handshake with " + neighborID + " is successful.");
				//pf.log.connected(neighborID);
			}
			else {
				System.out.println("Handshake failed.");
			}
			
			//bitfield exchange
			//send bitfield
			if (pp.allPeerInfo.get(peerID).bitField.length() > 0) { //if bitfield is not all null
				byte[] sendbf = null;
				synchronized(pp.allPeerInfo.get(peerID).bitField) {
					sendbf = pp.allPeerInfo.get(peerID).bitField.toByteArray();
				}
				synchronized(outMsg) {
					outMsg.writeInt(sendbf.length +1);
					outMsg.writeByte(5);
					outMsg.write(sendbf);
					outMsg.flush();
				}
			}
						
			//messages exchange process
			while (!end) {
				//receive messages
				int l = 0;
				byte type = 0;
				int idx = 0; //read piece index
				byte [] pl = new byte[pieceSize];
				byte [] bt = null; //read bitfield
				pl = new byte[pieceSize]; //read payload
				
				try {
					if(skt.isClosed())
						throw new SocketException();
					
					//receive message and extract msg type
					l = inMsg.readInt(); //read 4-byte length
					type = inMsg.readByte();
					bt = new byte[l];
				}
				catch(SocketTimeoutException | EOFException | SocketException ee) {
					//System.out.println(ee.getClass());
					inMsg.close();
					outMsg.close();
					skt.close();
					return;
				}
				catch(Exception e) {
					e.printStackTrace();
					return;
				}
				
				//send messages according to received message type
				switch(type) {
					case (byte)0: //choke
						pp.log.chokedNeighbor(neighborID);
						synchronized (pp.pf.haveRequested) {
							for (int i = 0; i < pp.pf.haveRequested.length; i++) {
								if (pp.pf.haveRequested[i] == neighborID)
									pp.pf.haveRequested[i] = -1;
							}
						}
						break;
						
					case (byte)1: //unchoke
						pp.log.unchokedNeighbor(neighborID);
						//System.out.println("Prepare to send request.(uncoked)");
						sendRequests();
						//System.out.println("Request sent.(unchoked)");
						break;
						
					case (byte)2: //interested
						pp.log.receiveInterested(neighborID);
						pp.interestList.add(neighborID);
						pp.allPeerInfo.get(neighborID).interested = true;
						break;
						
					case (byte)3: //not interested
						for(int i=0; i< pp.interestList.size(); i++) {
							if(pp.interestList.get(i)==neighborID) {
								pp.interestList.remove(i);
							}
						}
						pp.allPeerInfo.get(neighborID).interested = false;
						pp.log.receiveNotInterested(neighborID);
						break;
						
					case (byte)4: //have
						idx = inMsg.readInt(); //read piece #
						pp.log.receiveHave(neighborID, idx);
						synchronized(pp.allPeerInfo.get(neighborID).bitField) {
							pp.allPeerInfo.get(neighborID).bitField.set(idx);
							//interestedOrNot(pp.allPeerInfo.get(neighborID).bitField);
							interestedOrNot(this);
							if(pp.allPeerInfo.get(neighborID).bitField.cardinality() == pieceNm)
								pp.allPeerInfo.get(neighborID).exist = true;
						}
						break;
						
					case (byte)5: //bitfield
						inMsg.readFully(bt, 0, l-1);
						pp.allPeerInfo.get(neighborID).bitField = BitSet.valueOf(bt);
						//interestedOrNot(pp.allPeerInfo.get(neighborID).bitField);
						interestedOrNot(this);
						break;
						
					case (byte)6: //request
						idx = inMsg.readInt(); //read piece #
						if(unchokeState)
							sendPieces(idx);
						break;
						
					case (byte)7: //piece
						idx = inMsg.readInt();
						inMsg.readFully(pl, 0, l - 5);
						savePieces(idx, pl);
						System.out.println("Total pieces have been downloaded ###" + pp.allPeerInfo.get(peerID).bitField.cardinality());
						pp.log.downloadPiece(neighborID, idx, pp.allPeerInfo.get(peerID).bitField.cardinality());
						
						if(!pp.allPeerInfo.get(peerID).exist) {
							for(peer p : pp.peerList) {
								if(!want(p))
									p.sendNotInterested();
							}
							
							if (interestate) {
								//System.out.println("Prepare to send request.");
								sendRequests();
								//System.out.println("Sent request.");
							}
						}
						else {
							for (int i = 0; i < pp.peerList.size(); i++) {
								pp.peerList.get(i).sendNotInterested();
								pp.peerList.get(i).interestate = false;
							}
						}
						
						addSpeed();
						break;
						
					default:
						System.out.println("Unexpected message type.");
						break;
				}
			}
			inMsg.close();
			outMsg.close();
			skt.close();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	
	//functions to get and cumulate download speed
	public synchronized double getSpeed() {
		double c = speed;
		speed = 0; //reset
		return c;
	}
	
	public synchronized void addSpeed() {
		speed += pieceSize;
	}
	
	
	//send request message accordingly
	public void sendRequests() {
		int index;
		
		index = randomPiece(pp.allPeerInfo.get(neighborID).bitField);
		//System.out.println("Index " + index + " selected.");
		
		if (index != -1) {
			synchronized(pp.pf.haveRequested) {
				pp.pf.haveRequested[index] = neighborID;
			}
			try {
				//System.out.println("Waiting to access outMsg. " + index);
				synchronized(outMsg) {
					outMsg.writeInt(5);
					outMsg.writeByte(6);
					outMsg.writeInt(index);
					outMsg.flush();
				}
				//System.out.println("Done accessing outMsg. " + index);
			} catch (IOException e) {
				e.printStackTrace();
			}			
		}
	}
	
	//the peer selects a piece randomly among the pieces that its neighbor has
	//but it has not requested from other neighbors
	public int randomPiece(BitSet bitfield) {
		int r = -1;
		
		BitSet t = (BitSet)bitfield.clone();
		synchronized(pp.allPeerInfo.get(peerID).bitField) {
			t.andNot(pp.allPeerInfo.get(peerID).bitField);
		}
		
		Random rand = new Random();
		//System.out.println("Waiting to access haveRequested.");
		synchronized (pp.pf.haveRequested) {
			while (true) {
				boolean avail = false;
				for(int i = 0; i < pieceNm; i++) {
					if(t.get(i) && pp.pf.haveRequested[i] == -1) {
						avail = true;
						break;
					}
				}
				if(!avail)
					return -1;
				
				//returns a value between 0 (inclusive) and the specified value (exclusive)
				r = rand.nextInt(pieceNm);
				
				//target neighbor has && has not requested before
				if (t.get(r) && pp.pf.haveRequested[r] == -1)
					break;
			}
		}
		//System.out.println("Done accessing haveRequested.");
		
		return r;
	}
	
	
	//save requested pieces in the file
	public void savePieces(int idx, byte[] pl) throws IOException {
		if(!pp.allPeerInfo.get(peerID).bitField.get(idx)) {
			try {
				pp.pf.writePiece(pl,idx);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			pp.allPeerInfo.get(peerID).bitField.set(idx);
			
			if(pp.allPeerInfo.get(peerID).bitField.cardinality() == pieceNm) {
				//System.out.println("Complete");
				pp.allPeerInfo.get(peerID).exist = true;
				pp.log.completeDownload();
			}
			
			try {
				pp.broadcast(idx);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	//send requested pieces accordingly
	public void sendPieces(int idx) {
		byte[] temp = null;
		try {
			synchronized(outMsg) {
				temp = pp.pf.getpiece(idx);
				outMsg.writeInt(temp.length + 5);
				outMsg.writeByte(7);
				outMsg.writeInt(idx);
				outMsg.write(temp);
				outMsg.flush();
			}
			
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//decide whether a piece is needed or not
	public boolean want(peer p) {
		BitSet desire = (BitSet)pp.allPeerInfo.get(p.neighborID).bitField.clone();
		synchronized(pp.allPeerInfo.get(peerID).bitField) {
			desire.andNot(pp.allPeerInfo.get(peerID).bitField);
		}
		
		return (desire.length() == 0) ? false : true;
	}
	
	//send unchoke message and change unchokedstate
	public void unchoke() {
		try {
			synchronized(outMsg) {
				if(skt.isClosed())
					return;
				
				outMsg.writeInt(1);
				outMsg.writeByte(1);
				outMsg.flush();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		unchokeState = true;
	}
	
	//send choke message and change unchokedstate
	public void choke() {
		try {
			synchronized(outMsg) {
				if(skt.isClosed())
					return;
				
				outMsg.writeInt(1);
				outMsg.writeByte(0);
				outMsg.flush();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		unchokeState = false;
	}
	
	//send interested or not interested message
	public void interestedOrNot(peer p) {
		if (want(p) && !p.interestate) {
			//form a send message with type 2, interested
			sendInterested();
		}
		else if(!want(p) && p.interestate) {
			//form a send message with type 3, not interested
			sendNotInterested();
		}
	}
	
	public void sendInterested() {
		try {
			synchronized(outMsg) {
				outMsg.writeInt(1);
				outMsg.writeByte(2);
				outMsg.flush();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		interestate = true;
	}
	
	public void sendNotInterested() {
		try {
			synchronized(outMsg) {
				outMsg.writeInt(1);
				outMsg.writeByte(3);
				outMsg.flush();
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		interestate = false;
	}
	
	public void sendHaveMsg(int index, DataOutputStream out) throws IOException {
		synchronized(out) {
			out.writeInt(5);
			out.writeByte(4);
			out.writeInt(index);
			out.flush();
		}
	}
}

