package lphybeast;

import lphy.core.LPhyParser;
import lphy.parser.REPL;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(name = "lphybeast", version = "LPhyBEAST " + LPhyBEAST.VERSION, footer = "Copyright(c) 2020",
        description = "LPhyBEAST takes an LPhy model specification, and some data and produces a BEAST 2 XML file.")
public class LPhyBEAST implements Callable<Integer> {

    public static final String VERSION = "0.0.1 alpha";

    @Parameters(paramLabel = "LPhy", description = "File of the LPhy model specification")
    Path infile;

    @Option(names = {"-V", "--version"}, versionHelp = true, description = "display version info")
    boolean versionInfoRequested;
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean usageHelpRequested;

//    @Option(names = {"-wd", "--workdir"}, description = "Working directory") Path wd;
    @Option(names = {"-o", "--out"},     description = "BEAST 2 XML")  Path outfile;
//    @Option(names = {"-d", "--data"},    description = "File containing alignment or traits")
    Path datafile;
//    @Option(names = {"-m", "--mapping"}, description = "mapping file") Path mapfile;


    public static void main(String[] args) throws IOException {

        int exitCode = new CommandLine(new LPhyBEAST()).execute(args);
        System.exit(exitCode);

    }


    @Override
    public Integer call() throws Exception { // business logic goes here...

        BufferedReader reader = new BufferedReader(new FileReader(infile.toFile()));

        //*** Parse LPhy file ***//
        LPhyParser parser = new REPL();
        source(reader, parser);

        BEASTContext context = new BEASTContext(parser);

        //*** Write BEAST 2 XML ***//
        String wkdir = infile.getParent().toString();
        String fileName = infile.getFileName().toString();
        String fileNameStem = fileName.substring(0, fileName.indexOf("."));

        String xml = context.toBEASTXML(fileNameStem);

        String outfileName = fileNameStem + ".xml";
        if (outfile == null) {
            // create outfile in the same dir of infile as default
            outfile = Paths.get(wkdir, outfileName);
        }

        PrintWriter writer = new PrintWriter(new FileWriter(outfile.toFile()));

        writer.println(xml);
        writer.flush();
        writer.close();

        System.out.println("\nCreate BEAST 2 XML : " + outfile);
        return 0;
    }

    private static void source(BufferedReader reader, LPhyParser parser) throws IOException {
        String line = reader.readLine();
        while (line != null) {
            parser.parse(line);
            line = reader.readLine();
        }
        reader.close();
    }

}
