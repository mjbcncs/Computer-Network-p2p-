import java.io.*;
import java.util.*;
import java.text.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class logWriting {

	private int currentPeerID;
	private FileWriter logFile;
	private StringBuffer logInfo;
	
	//constructor
	public logWriting(int id) throws IOException {
		currentPeerID = id;
		String filenm = "log_peer_" + id + ".log";
		logFile = new FileWriter(filenm);
	}
	
	private static String getCurrentTime() {
		SimpleDateFormat time;
		time = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]: ");	
		return time.format(new Date());
	}
	
	public void connect (int peerID) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " makes a connection to Peer " + peerID + "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void connected (int peerID) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " is connected from Peer " + peerID + "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void changePreferredNeighbors (CopyOnWriteArrayList<Integer> preferNbList) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " has the preferred neighbors ");
		if (preferNbList.isEmpty()) {
			logInfo.append("of null."+ "\n");
		}
		else {
			for(int i=0;i<preferNbList.size();i++)
				if (i == preferNbList.size()-1)
					logInfo.append(preferNbList.get(i) + "."+ "\n");
				else
					logInfo.append(preferNbList.get(i) + ", ");
		}
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void changeOptimisticNeighbor (int peerID) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " has the optimistically unchoked neighbor " + peerID+ "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void unchokedNeighbor (int peerID) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " is unchoked by " + peerID+ "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void chokedNeighbor (int peerID) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " is choked by " + peerID+ "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void receiveHave (int peerID, int pieceIndex) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " received the 'have' message from " + peerID
				+ " for the piece " + pieceIndex+ "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void receiveInterested (int peerID) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " received the 'interested' message from " + peerID+ "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void receiveNotInterested (int peerID) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " received the 'not interested' message from " + peerID+ "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void downloadPiece (int peerID, int pieceIndex, int pieceNum) throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " has downloaded the piece " + pieceIndex
				+ " from " + peerID + ". Now the number of pieces it has is " + pieceNum+ "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
	
	public void completeDownload () throws IOException {
		logInfo = new StringBuffer();
		logInfo.append(getCurrentTime() + "Peer " + currentPeerID + " has downloaded the complete file."+ "\n");
		synchronized(logFile) {
			logFile.write(logInfo.toString());
			logFile.flush();
		}
	}
}