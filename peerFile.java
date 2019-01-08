import java.io.*;
import java.util.*;
import java.lang.*;

public class peerFile {
	peerProcess pp;
	int peerID;
	String fileName;
	int fileSize;
	int pieceSize;
	int pieceNm;
	RandomAccessFile rafile;
	int[] haveRequested;
	
	public peerFile (peerProcess pp, int id, String fileName, int fileSize, int pieceSize, int pieceNm) throws IOException {
		this.pp = pp;
		peerID = id;
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.pieceSize = pieceSize;
		this.pieceNm = pieceNm;
		
		String dir = "peer_" + id;
        File ff = new File(dir);
        if (!ff.exists()){
        		try {
                ff.mkdir();
            } catch(SecurityException e) {
                System.err.println(e);
            }        
        }
        
        if (pp.allPeerInfo.get(pp.currentPeerID).exist == true) {
        		rafile = new RandomAccessFile(dir + "/" + this.fileName, "r");
            //"rw": open for reading and writing, if the file does not already exist then an attempt will be made to create it
        		if(rafile.length() != fileSize)
        			System.out.println("No file exist!");
        }
        else {
        		rafile = new RandomAccessFile(dir + "/" + this.fileName, "rw");
        		rafile.setLength(fileSize);
        }
        
        if(pp.allPeerInfo.get(pp.currentPeerID).exist == true) {
        		//Sets the bits from index 0 to the index bitLength to true
			pp.allPeerInfo.get(peerID).bitField.set(0,pieceNm);
		}
        
        //check whether a specific piece has been requested, initially all is 0 (not requested)
        haveRequested = new int[pieceNm];
        for(int i = 0;i < pieceNm; i++) {
        		haveRequested[i] = -1;
        }
         
	}
	
	//write correct piece of file
	public void writePiece(byte[] content, int index) throws IOException {
		int byteLength;
		if (index == pieceNm - 1) {
			//last piece of file
			byteLength = fileSize - (pieceSize * (pieceNm-1));
		}
		else {
			//not the last piece of file
			byteLength = pieceSize;
		}
		synchronized(rafile) {
			
			rafile.seek((long)index*pieceSize);
			rafile.write(content, 0, byteLength);
		}

	}
	
	//read correct piece of file
	public byte[] getpiece(int index) throws IOException {
		int byteLength;
		byte [] r = new byte [0];
		if (index == pieceNm - 1) {
			//last piece of file
			byteLength = fileSize - (pieceSize * (pieceNm-1));
		}
		else {
			//not the last piece of file
			byteLength = pieceSize;
		}
		
		byte [] piece = new byte[byteLength];
		synchronized(rafile) {
			rafile.seek((long)index * pieceSize);
			rafile.readFully(piece, 0, byteLength);
		}
		//Reads up to b.length bytes of data from this file into an array of bytes
		return piece;
	}
}
