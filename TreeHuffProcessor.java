import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.PriorityQueue;

public class TreeHuffProcessor implements IHuffProcessor {
    
    private HuffViewer myViewer;
    int[] freqs = new int[ALPH_SIZE+1];
    String[] map = new String[ALPH_SIZE+1];
    int totalUncompressedBits;
    int totalCompressedBits;
    TreeNode myRoot;
    int totalCompressedBitsWritten;
    int totalUncompressedBitsWritten;

    /**
     * Count the number of times each char/chunk occurs, create the
     * Huffman tree, and build a map from char/chunk to encoding
     */
    public int preprocessCompress(InputStream in) throws IOException {
        //throw new IOException("preprocess not implemented");
        freqs = new int[ALPH_SIZE+1];
        map = new String[ALPH_SIZE+1];
        totalUncompressedBits = 0;
        totalCompressedBits = 0;
        myRoot = null;
    	if (in == null)
        	return 0;
    	BitInputStream bis = new BitInputStream(in);
    	//int[] freqs = new int[ALPH_SIZE];
    	int chunk;
    	totalUncompressedBits = 0;
    	while ((chunk = bis.readBits(BITS_PER_WORD)) != -1) {
    		freqs[chunk] ++;
    		totalUncompressedBits += BITS_PER_WORD;
    	}
    	freqs[PSEUDO_EOF] = 1;
    	// from freqs create a priority queue
    	PriorityQueue<TreeNode> queue = new PriorityQueue<TreeNode>();
    	for (int ch = 0; ch < freqs.length; ch++) {
    		if (freqs[ch] != 0) {
    			queue.add(new TreeNode(ch, freqs[ch], null, null));
    		}
    	}
    	// from priority queue create a Huffman tree
    	while (queue.size() > 1) {
    		TreeNode left = queue.poll();
    		TreeNode right = queue.poll();
    		queue.add(new TreeNode(0, left.myWeight + right.myWeight, left, right));
    	}
    	// from Huffman tree create map
    	myRoot = queue.peek();
    	totalCompressedBits = 0;
    	createCodings(myRoot, "");
    	// find out how many bits were saved
    	// find out total bits in compressed file
    	bis.close();
    	return totalUncompressedBits - totalCompressedBits;
    }
    
    public void createCodings(TreeNode root, String path) {
    	if (root.myLeft != null && root.myRight != null) {
    		createCodings(root.myLeft, path + "0");
    		createCodings(root.myRight, path + "1");
    	}
    	if (root.myLeft == null && root.myRight == null) {
    		map[root.myValue] = path;
    		totalCompressedBits += freqs[root.myValue]*path.length();
    	}
    	
    }

    public void setViewer(HuffViewer viewer) {
        myViewer = viewer;
    }

    public int compress(InputStream in, OutputStream out, boolean force) throws IOException {
    	// forcing compression
    	if (totalCompressedBits >= totalUncompressedBits && force == false) {
    		int difference = totalCompressedBits - totalUncompressedBits;
    		throw new IOException("compression uses " + difference + " more bits. Use force compression to compress");
    	}
    	totalCompressedBitsWritten = 0;
    	// throw new IOException("compress is not implemented");
        BitOutputStream bos = new BitOutputStream(out);
        // magic number
        bos.writeBits(BITS_PER_INT, MAGIC_NUMBER);
        totalCompressedBitsWritten += BITS_PER_INT;
        // header
        createHeader(myRoot, bos);
        // write out the bits
        BitInputStream bis = new BitInputStream(in);
        int chunk;
        while ((chunk = bis.readBits(BITS_PER_WORD)) != -1) {
    		for (int i = 0; i < map[chunk].length(); i++) {
    			String digit = map[chunk].substring(i,i+1);
    			bos.writeBits(1, (Integer.parseInt(digit) & 1));
    			totalCompressedBitsWritten ++;
    		}
    	}
        for (int i = 0; i < map[PSEUDO_EOF].length(); i++) {
        	String digit = map[PSEUDO_EOF].substring(i,i+1);
        	bos.writeBits(1, (Integer.parseInt(digit) & 1));
        	totalCompressedBitsWritten ++;
        }
        bis.close();
        bos.close();
    	return totalCompressedBitsWritten;
    }
    
    public void createHeader(TreeNode root, BitOutputStream bos) {
    	int zero = 0;
    	int one = 1;
    	if (root.myLeft != null && root.myRight != null) {
    		bos.writeBits(1, (zero & 1));
    		totalCompressedBitsWritten ++;
    		createHeader(root.myLeft, bos);
    		createHeader(root.myRight, bos);
    	}
    	if (root.myLeft == null && root.myRight == null) {
    		bos.writeBits(1, (one & 1));
    		bos.writeBits(BITS_PER_WORD+1, root.myValue);
    		totalCompressedBitsWritten += BITS_PER_WORD + 2;
    	}
    }
    
    public int uncompress(InputStream in, OutputStream out) throws IOException {
        // throw new IOException("uncompress not implemented");
        totalUncompressedBitsWritten = 0;
    	BitInputStream bis = new BitInputStream(in);
        BitOutputStream bos = new BitOutputStream(out);
        int magic = bis.readBits(BITS_PER_INT);
        if (magic != MAGIC_NUMBER && magic != STORE_COUNTS && magic != STORE_TREE && magic != STORE_CUSTOM) {
        	throw new IOException("magic number not correct");
        }
        //decodeMap = new HashMap<String, Integer>();
        TreeNode root = createTreeFromHeader(bis, "");
        TreeNode tnode = root;
        while (true) {
        	int bits = bis.readBits(1);
        	if (bits == -1) {
        		throw new IOException("error reading bits, no PSEUDO-EOF");
        	}
        	// use the zero/one value of the bit read
        	// to traverse Huffman coding tree
        	// if a leaf is reached, decode the character and print UNLESS
        	// the character is the pseudo-EOF, then decompression done
        	if ((bits & 1) == 0) {
        		tnode = tnode.myLeft;
        	}
        	else {
        		tnode = tnode.myRight;
        	}
        	if (tnode.myLeft == null && tnode.myRight == null) {
        		if (tnode.myValue == PSEUDO_EOF) {
        			break;
        		}
        		else {
        			bos.writeBits(BITS_PER_WORD, tnode.myValue);
        			tnode = root;
        			totalUncompressedBitsWritten += BITS_PER_WORD;
        		}
        	}
        }
        bis.close();
        bos.close();
    	return totalUncompressedBits;
    }
    
    public TreeNode createTreeFromHeader(BitInputStream bis, String path) throws IOException {
    	int leaf = bis.readBits(1);
    	TreeNode node = null;
    	if ((leaf & 1) == 0) {
    		node = new TreeNode(0, 0, null, null);
    		node.myLeft = createTreeFromHeader(bis, path + "0");
    		node.myRight = createTreeFromHeader(bis, path + "1");
    	}
    	if ((leaf & 1) == 1) {
    		int val = bis.readBits(BITS_PER_WORD + 1);
    		node = new TreeNode(val, 0, null, null);
    	}
    	return node;
    }
    
    private void showString(String s){
        myViewer.update(s);
    }

}
