import java.io.*;
import java.util.*;
import java.util.regex.*;

class FunctionNode {
    public HashSet<FunctionNode> callees;
    public HashSet<FunctionNode> callers;
    public String name;
    public int support;
    public Map<FunctionNode, Integer> pairwiseSupport;

    public FunctionNode () {
        callees = new HashSet<FunctionNode>();
        callers = new HashSet<FunctionNode>();
        pairwiseSupport = new HashMap<FunctionNode, Integer>();
    }
}

public class LikelyInvariants {
    public static String[] sortStringPair(String a, String b) {
        if (a.compareTo(b) < 0)
            return new String[] {a, b};
        else
            return new String[] {b, a};
    }

    static void printBug(FunctionNode nodeA, FunctionNode nodeB, FunctionNode caller,
        String[] sortedPair, double supAB, double supA) {
        System.out.format("bug: %s in %s, pair: (%s, %s), " +
            "support: %d, confidence: %.2f%%",
            nodeA.name, caller.name, sortedPair[0], sortedPair[1],
            nodeA.pairwiseSupport.get(nodeB), supAB * 100d / supA);
        System.out.println();
    }

    static void PopulateSupports(FunctionNode root) {
        for (FunctionNode calleeA : root.callees) {
            calleeA.support++;
            for (FunctionNode calleeB : root.callees) {
                if (calleeA != calleeB) {
                    Map<FunctionNode, Integer> supportMap = calleeA.pairwiseSupport;
                    int support = supportMap.containsKey(calleeB) ? supportMap.get(calleeB) : 0;
                    supportMap.put(calleeB, support + 1);
                }
            }
        }
    }

    static void InterProceduralAnalysis(Collection<FunctionNode> nodes, int depth) {
        // Need to buffer each set of callees
        Map<FunctionNode, HashSet<FunctionNode>> calleeMap = 
            new HashMap<FunctionNode, HashSet<FunctionNode>>();

        for (FunctionNode node : nodes) {
            HashSet<FunctionNode> calleesCopy = new HashSet<FunctionNode>(node.callees);
            for (FunctionNode callee : node.callees) {
                ExpandCalleesCallers(node, callee, calleesCopy, depth);
            }
            calleeMap.put(node, calleesCopy); 
        }

        for (FunctionNode node : nodes) {
            node.callees = calleeMap.get(node);
        }
    }

    static void ExpandCalleesCallers(FunctionNode root, FunctionNode currentCallee,
        HashSet<FunctionNode> callees, int depth) { 
        if (depth < 1) {
            return;
        }

        for (FunctionNode callee : currentCallee.callees) {
            if (!callees.contains(callee)) {
                callees.add(callee);
                ExpandCalleesCallers(root, callee, callees, depth - 1);
            
                if (!callee.callers.contains(root)) {
                    callee.callers.add(root);
                }
            }
        }
    }

    static void PrintNode(FunctionNode node) {
        System.out.println(node.name + ":");
        System.out.println("  Callees:");
        for (FunctionNode callee : node.callees) {
            System.out.println("    " + callee.name);
        }
        System.out.println("  Callers:");
        for (FunctionNode caller : node.callers) {
            System.out.println("    " + caller.name);
        }
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        double epsilon = 0.000001d;

        // Make sure there are 4 args
        if (args.length != 4) {
            System.err.println("Usage:");
            System.err.println("  LikelyInvariants [callgraph file] [support threshold] [confidence threshold] [inter-procedural depth]");
            return;
        }

        // Try to convert args 2, 3 and 4 to int, double and int, resp.
        int supportThreshold;
        double confidenceThreshold;
        int interDepth;
        try {
            supportThreshold = Integer.parseInt(args[1]);
            confidenceThreshold = Double.parseDouble(args[2]) / 100d;
            interDepth = Integer.parseInt(args[3]);
        }
        catch (Exception e) {
            System.err.println("Could not parse the parameters");
            return;
        }

        // Read the callgraph
        Map<String, FunctionNode> nodeMap = new HashMap<String, FunctionNode>();

        try {
            BufferedReader graphFile = null;

            try {
                graphFile = new BufferedReader(new FileReader(args[0]));
                ArrayList<String> callgraph = new ArrayList<String>();
                String line;
                FunctionNode currentRoot = null;

                Pattern rootNodePattern = Pattern.compile(
                    "(?:Call graph node for function: ')" +
                    "([_a-zA-Z][_a-zA-Z0-9]*)" +
                    "(?:'<<0x)(?:[0-9a-fA-F]+)(?:>>  #uses=)(?:\\d+)");

                Pattern calleePattern = Pattern.compile(
                    "(?:  CS<0x)" +
                    "(?:[0-9a-fA-F]+)" +
                    "(?:> calls function ')([_a-zA-Z][_a-zA-Z0-9]*)(?:')");

                // Parse the lines
                while ((line = graphFile.readLine()) != null) {

                    // Skip empty lines (there's a lot of them, and
                    // it's slightly faster than going through the regexp)
                    if (line.length() == 0)
                        continue;

                    // Check for a root function node
                    Matcher rootMatcher = rootNodePattern.matcher(line);
                    if (rootMatcher.matches()) {
                        String rootName = rootMatcher.group(1);
                        FunctionNode root;
                        if (!nodeMap.containsKey(rootName)) {
                            root = new FunctionNode();
                            root.name = rootName;
                            nodeMap.put(rootName, root);
                        }
                        else {
                            root = nodeMap.get(rootName);
                        }
                        currentRoot = root;
                    }

                    // Check for a callee
                    else {
                        Matcher calleeMatcher = calleePattern.matcher(line);
                        if (calleeMatcher.matches()) {
                            String calleeName = calleeMatcher.group(1);
                            FunctionNode callee;
                            if (!nodeMap.containsKey(calleeName)) {
                                callee = new FunctionNode();
                                callee.name = calleeName;
                                nodeMap.put(calleeName, callee);
                            }
                            else {
                                callee = nodeMap.get(calleeName);
                            }

                            if (currentRoot != null) {
                                if (!currentRoot.callees.contains(callee)) {
                                    currentRoot.callees.add(callee);
                                }
                                if (!callee.callers.contains(currentRoot)) {
                                    callee.callers.add(currentRoot);
                                }
                            }
                        }
                    }
                }
            }
            finally {
                if (graphFile != null) {
                    graphFile.close();
                }
            }
        }
        catch (IOException e) {
            System.err.println("Exception while trying to load callgraph.tmp: " + e.getMessage());
            return;
        }
 
        // Perform inter-procedural analysis
        InterProceduralAnalysis(nodeMap.values(), interDepth);

        // Populate supports
        for (FunctionNode func : nodeMap.values()) {
            PopulateSupports(func);
        }

        // Find likely invariants
        for (FunctionNode nodeA : nodeMap.values()) {
            for (FunctionNode nodeB : nodeA.pairwiseSupport.keySet()) {
                double supA = (double)nodeA.support;
                double supB = (double)nodeB.support;
                double supAB = (double)nodeA.pairwiseSupport.get(nodeB) + epsilon;

                if (supAB >= (double)supportThreshold &&
                    supAB / supA >= confidenceThreshold) {

                    String[] sortedPair = sortStringPair(nodeA.name, nodeB.name);
                    
                    for (FunctionNode caller : nodeA.callers) {
                        if (!caller.callees.contains(nodeB)) {
                            printBug(nodeA, nodeB, caller, sortedPair, supAB, supA);
                        }
                    }
                }

                if (supAB >= (double)supportThreshold &&
                    supAB / supB >= confidenceThreshold) {

                    String[] sortedPair = sortStringPair(nodeA.name, nodeB.name);
                    
                    for (FunctionNode caller : nodeB.callers) {
                        if (!caller.callees.contains(nodeA)) {
                            printBug(nodeB, nodeA, caller, sortedPair, supAB, supB);
                        }
                    }
                }

                nodeB.pairwiseSupport.remove(nodeA);
            }
            nodeA.pairwiseSupport.clear();
        }

        long end = System.currentTimeMillis();
        //System.out.println(end - start);
    }
}
