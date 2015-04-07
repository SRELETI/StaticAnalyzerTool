# StaticAnalyzerTool

The program expects either 1 or 3 or 4 arguments.

First argument = Java source file
Second argument =  Support value
Third argument = Confidence
Fourth argument = Depth of inter procedural analysis

if [ ! $# -eq 1 ] && [ ! $# -eq 3 ] && [ ! $# -eq 4 ]
    then
        echo "Incorrect number of arguments"
        exit 1
fi

opt -print-callgraph $1 2>callgraph.tmp 1>/dev/null

if [ $# -eq 4 ]
    then
        java -Xmx128M -cp `dirname ${BASH_SOURCE[0]}` LikelyInvariants callgraph.tmp $2 $3 $4 2>/dev/null
elif [ $# -eq 3 ]
    then
        java -Xmx128M -cp `dirname ${BASH_SOURCE[0]}` LikelyInvariants callgraph.tmp $2 $3 2>/dev/null
elif [ $# -eq 1 ]
    then
        java -Xmx128M -cp `dirname ${BASH_SOURCE[0]}` LikelyInvariants callgraph.tmp 3 65 2>/dev/null
fi

rm callgraph.tmp
