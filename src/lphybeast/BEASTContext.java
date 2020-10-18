package lphybeast;

import beast.core.Loggable;
import beast.core.*;
import beast.core.parameter.BooleanParameter;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.Parameter;
import beast.core.parameter.RealParameter;
import beast.core.util.CompoundDistribution;
import beast.evolution.alignment.Taxon;
import beast.evolution.operators.*;
import beast.evolution.substitutionmodel.Frequencies;
import beast.evolution.tree.Tree;
import beast.math.distributions.ParametricDistribution;
import beast.math.distributions.Prior;
import beast.util.XMLProducer;
import lphy.core.LPhyParser;
import lphy.core.distributions.Dirichlet;
import lphy.core.distributions.RandomComposition;
import lphy.graphicalModel.*;
import lphybeast.tobeast.generators.*;
import lphybeast.tobeast.values.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class BEASTContext {

    List<StateNode> state = new ArrayList<>();

    Set<BEASTInterface> elements = new HashSet<>();
    List<StateNodeInitialiser> inits = new ArrayList<>();

    // a map of graphical model nodes to equivalent BEASTInterface objects
    private Map<GraphicalModelNode<?>, BEASTInterface> beastObjects = new HashMap<>();

    // a map of BEASTInterface to graphical model nodes that they represent
    Map<BEASTInterface, GraphicalModelNode<?>> BEASTToLPHYMap = new HashMap<>();

    Map<Class, ValueToBEAST> valueToBEASTMap = new HashMap<>();
    Map<Class, GeneratorToBEAST> generatorToBEASTMap = new HashMap<>();

    private List<Operator> extraOperators = new ArrayList<>();
    private List<Loggable> extraLoggables = new ArrayList<>();

    SortedMap<String, Taxon> allTaxa = new TreeMap<>();

    LPhyParser parser;


    public BEASTContext(LPhyParser phyParser) {
        parser = phyParser;
        registerValues();
        registerGenerators();
    }

    private void registerValues() {
        final Class[] valuesToBEASTs = {
                AlignmentToBEAST.class, // simulated alignment
                TimeTreeToBEAST.class,
//                MapValueToBEAST.class,
                DoubleValueToBEAST.class,
                DoubleArrayValueToBEAST.class,
                NumberArrayValueToBEAST.class,
                DoubleArray2DValueToBEAST.class,
                IntegerValueToBEAST.class,
                IntegerArrayValueToBEAST.class,
                BooleanArrayValueToBEAST.class,
                BooleanValueToBEAST.class
        };

        for (Class c : valuesToBEASTs) {
            try {
                ValueToBEAST valueToBEAST = (ValueToBEAST) c.newInstance();
                valueToBEASTMap.put(valueToBEAST.getValueClass(), valueToBEAST);
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void registerGenerators() {
        final Class[] generatorToBEASTs = {
                BernoulliMultiToBEAST.class,
                BetaToBEAST.class,
                BirthDeathSerialSamplingToBEAST.class,
                BirthDeathSampleTreeDTToBEAST.class,
                DirichletToBEAST.class,
                ExpToBEAST.class,
                F81ToBEAST.class,
                FossilBirthDeathTreeToBEAST.class,
                GammaToBEAST.class,
                GTRToBEAST.class,
                HKYToBEAST.class,
                InverseGammaToBEAST.class,
                InverseGammaMultiToBEAST.class,
                JukesCantorToBEAST.class,
                K80ToBEAST.class,
                LewisMKToBeast.class,
                LocalBranchRatesToBEAST.class,
                LogNormalMultiToBEAST.class,
                LogNormalToBEAST.class,
                MultispeciesCoalescentToStarBEAST2.class,
                NormalMultiToBEAST.class,
                NormalToBEAST.class,
                PhyloCTMCToBEAST.class,
                PoissonToBEAST.class,
                SkylineToBSP.class,
                SerialCoalescentToBEAST.class,
                StructuredCoalescentToMascot.class,
                TreeLengthToBEAST.class,
                TN93ToBEAST.class,
                UniformToBEAST.class,
                YuleToBEAST.class,
                ExpMarkovChainToBEAST.class
        };

        for (Class c : generatorToBEASTs) {
            try {
                GeneratorToBEAST generatorToBEAST = (GeneratorToBEAST) c.newInstance();
                generatorToBEASTMap.put(generatorToBEAST.getGeneratorClass(), generatorToBEAST);
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public BEASTInterface getBEASTObject(GraphicalModelNode<?> node) {
        return beastObjects.get(node);
    }

    public BEASTInterface getBEASTObject(String id) {
        for (BEASTInterface beastInterface : elements) {
            if (id.equals(beastInterface.getID())) return beastInterface;
        }

        for (BEASTInterface beastInterface : beastObjects.values()) {
            if (id.equals(beastInterface.getID().equals(id))) return beastInterface;
        }
        return null;
    }

    public RealParameter getAsRealParameter(Value<Number> value) {
        Parameter param = (Parameter)beastObjects.get(value);
        if (param instanceof RealParameter) return (RealParameter)param;
        if (param instanceof IntegerParameter) {
            RealParameter newParam = createRealParameter(param.getID(), ((IntegerParameter) param).getValue());
            beastObjects.put(value, newParam);
            return newParam;
        }
        throw new RuntimeException("No coercable parameter found.");
    }

    public GraphicalModelNode getGraphicalModelNode(BEASTInterface beastInterface) {
        return BEASTToLPHYMap.get(beastInterface);
    }

    public void addBEASTObject(BEASTInterface newBEASTObject) {
        elements.add(newBEASTObject);
    }

    public void addStateNode(StateNode stateNode) {
        if (!state.contains(stateNode)) {
            elements.add(stateNode);
            state.add(stateNode);
        }
    }

    public void removeBEASTObject(BEASTInterface beastObject) {
        elements.remove(beastObject);
        state.remove(beastObject);
        BEASTToLPHYMap.remove(beastObject);

        GraphicalModelNode matchingKey = null;
        for (GraphicalModelNode key : beastObjects.keySet()) {
            if (getBEASTObject(key) == beastObject) {
                matchingKey = key;
                break;
            }
        }
        if (matchingKey != null) beastObjects.remove(matchingKey);
    }

    public static RealParameter createRealParameter(Double[] value) {
        return new RealParameter(value);
    }

    public static RealParameter createRealParameter(double value) {
        return createRealParameter(null, value);
    }

    public static RealParameter createRealParameter(String id, double value) {
        RealParameter parameter = new RealParameter();
        parameter.setInputValue("value", value);
        parameter.initAndValidate();
        if (id != null) parameter.setID(id);
        return parameter;
    }

    /**
     * Make a BEAST2 model from the current model in parser.
     */
    public void createBEASTObjects() {

        Set<Value<?>> sinks = parser.getModelSinks();

        for (Value<?> value : sinks) {
            createBEASTValueObjects(value);
        }

        Set<Generator> visited = new HashSet<>();
        for (Value<?> value : sinks) {
            traverseBEASTGeneratorObjects(value, true, false, visited);
        }

        visited.clear();
        for (Value<?> value : sinks) {
            traverseBEASTGeneratorObjects(value, false, true, visited);
        }
    }

    /**
     * @param id
     * @return true if the given id has a value in the data block and random variable in the model block
     */
    public boolean isClamped(String id) {
        if (id != null) {
            Value dataValue = parser.getValue(id, LPhyParser.Context.data);
            Value modelValue = parser.getModelDictionary().get(id);
            return (dataValue != null && modelValue != null && modelValue instanceof RandomVariable);
        }
        return false;
    }

    /**
     * @param id the id of the value
     * @return the value with this id from the data context if it exits, or if not, then the value from the model context if exists, or if neither exist, then returns null.
     */
    public Value getClampedValue(String id) {
        if (id != null) {
            Value clampedValue = parser.getValue(id, LPhyParser.Context.data);
            if (clampedValue != null) {
                return clampedValue;
            }
            return  parser.getValue(id, LPhyParser.Context.model);
        }
        return null;
    }

    private void createBEASTValueObjects(Value<?> value) {

        if (beastObjects.get(value) == null) {
            valueToBEAST(value);
        }

        Generator<?> generator = value.getGenerator();
        if (generator != null) {

            for (Object inputObject : generator.getParams().values()) {
                Value<?> input = (Value<?>) inputObject;
                createBEASTValueObjects(input);
            }
        }
    }


    private void traverseBEASTGeneratorObjects(Value<?> value, boolean modifyValues, boolean createGenerators, Set<Generator> visited) {

        Generator<?> generator = value.getGenerator();
        if (generator != null) {

            for (Object inputObject : generator.getParams().values()) {
                Value<?> input = (Value<?>) inputObject;
                traverseBEASTGeneratorObjects(input, modifyValues, createGenerators, visited);
            }

            if (!visited.contains(generator)) {
                generatorToBEAST(value, generator, modifyValues, createGenerators);
                visited.add(generator);
            }
        }
    }

    /**
     * This is called after valueToBEAST has been called on both the generated value and the input values.
     * Side-effect of this method is to create an equivalent BEAST object of the generator and put it in the beastObjects map of this BEASTContext.
     *
     * @param value
     * @param generator
     */
    private void generatorToBEAST(Value value, Generator generator, boolean modifyValues, boolean createGenerators) {

        if (getBEASTObject(generator) == null) {

            BEASTInterface beastGenerator = null;

            GeneratorToBEAST toBEAST = generatorToBEASTMap.get(generator.getClass());

            if (toBEAST != null) {
                BEASTInterface beastValue = beastObjects.get(value);
                // If this is a generative distribution then swap to the clamped value if it exists
                if (generator instanceof GenerativeDistribution && isClamped(value.getId())) {
                    beastValue = getBEASTObject(getClampedValue(value.getId()));
                }

                if (modifyValues) {
                    toBEAST.modifyBEASTValues(generator, beastValue, this);
                }
                if (createGenerators) {
                    beastGenerator = toBEAST.generatorToBEAST(generator, beastValue, this);
                }
            }

            if (createGenerators) {
                if (beastGenerator == null) {
                    if (!Exclusion.isExcludedGenerator(generator))
                        throw new UnsupportedOperationException("Unhandled generator in generatorToBEAST(): " + generator);
                } else {
                    addToContext(generator, beastGenerator);
                }
            }
        }
    }

    private BEASTInterface valueToBEAST(Value<?> val) {

        BEASTInterface beastValue = null;

        ValueToBEAST toBEAST = valueToBEASTMap.get(val.value().getClass());

        if (toBEAST != null) {
            // if *ToBEAST has not been initiated
            beastValue = toBEAST.valueToBEAST(val, this);
        } else {
            for (Class c : valueToBEASTMap.keySet()) {
                // if *ToBEAST exists
                if (c.isAssignableFrom(val.value().getClass())) {
                    toBEAST = valueToBEASTMap.get(c);
                    beastValue = toBEAST.valueToBEAST(val, this);
                }
            }
        }
        if (beastValue == null) {
            // ignore all String: d = nexus(file="Dengue4.nex");
            if (! Exclusion.isExcludedValue(val) )
                 throw new UnsupportedOperationException("Unhandled value in valueToBEAST(): \"" +
                    val + "\" of type " + val.value().getClass());
        } else {
            addToContext(val, beastValue);
        }
        return beastValue;
    }

    private void addToContext(GraphicalModelNode node, BEASTInterface beastInterface) {
        beastObjects.put(node, beastInterface);
        BEASTToLPHYMap.put(beastInterface, node);
        elements.add(beastInterface);

        if (node instanceof RandomVariable) {
            RandomVariable<?> var = (RandomVariable<?>) node;

            if (var.getOutputs().size() > 0 && !state.contains(beastInterface)) {
                state.add((StateNode) beastInterface);
            }
        }
    }

    /**
     * @param freqParameter
     * @param stateNames    the names of the states in a space-delimited string
     * @return
     */
    public static Frequencies createBEASTFrequencies(RealParameter freqParameter, String stateNames) {
        Frequencies frequencies = new Frequencies();
        frequencies.setInputValue("frequencies", freqParameter);
        freqParameter.setInputValue("keys", stateNames);
        freqParameter.initAndValidate();
        frequencies.initAndValidate();
        return frequencies;
    }

    public static Prior createPrior(ParametricDistribution distr, Parameter parameter) {
        Prior prior = new Prior();
        prior.setInputValue("distr", distr);
        prior.setInputValue("x", parameter);
        prior.initAndValidate();
        prior.setID(parameter.getID() + ".prior");
        return prior;
    }


    public List<Operator> createOperators() {

        List<Operator> operators = new ArrayList<>();

        for (StateNode stateNode : state) {
            System.out.println("State node" + stateNode);
            if (stateNode instanceof RealParameter) {
                operators.add(createBEASTOperator((RealParameter) stateNode));
            } else if (stateNode instanceof IntegerParameter) {
                operators.add(createBEASTOperator((IntegerParameter) stateNode));
            } else if (stateNode instanceof BooleanParameter) {
                operators.add(createBEASTOperator((BooleanParameter) stateNode));
            } else if (stateNode instanceof Tree) {
                operators.add(createTreeScaleOperator((Tree) stateNode));
                operators.add(createExchangeOperator((Tree) stateNode, true));
                operators.add(createExchangeOperator((Tree) stateNode, false));
                operators.add(createSubtreeSlideOperator((Tree) stateNode));
                operators.add(createTreeUniformOperator((Tree) stateNode));
            }
        }

        operators.addAll(extraOperators);
        operators.sort(Comparator.comparing(BEASTObject::getID));

        return operators;
    }

    private List<Logger> createLoggers(int logEvery, String fileName) {
        List<Logger> loggers = new ArrayList<>();

        loggers.add(createScreenLogger(logEvery));
        loggers.add(createLogger(logEvery, fileName + ".log"));
        loggers.addAll(createTreeLoggers(logEvery, fileName));

        return loggers;
    }

    private Logger createLogger(int logEvery, String fileName) {

        List<Loggable> nonTrees = state.stream()
                .filter(stateNode -> !(stateNode instanceof Tree))
                .collect(Collectors.toList());

        nonTrees.addAll(extraLoggables);

        Logger logger = new Logger();
        logger.setInputValue("logEvery", logEvery);
        logger.setInputValue("log", nonTrees);
        if (fileName != null) logger.setInputValue("fileName", fileName);
        logger.initAndValidate();
        elements.add(logger);
        return logger;
    }

    private List<Logger> createTreeLoggers(int logEvery, String fileNameStem) {

        List<Tree> trees = state.stream()
                .filter(stateNode -> stateNode instanceof Tree)
                .map(stateNode -> (Tree) stateNode)
                .sorted(Comparator.comparing(BEASTObject::getID))
                .collect(Collectors.toList());

        boolean multipleTrees = trees.size() > 1;

        List<Logger> treeLoggers = new ArrayList<>();

        for (Tree tree : trees) {
            Logger logger = new Logger();
            logger.setInputValue("logEvery", logEvery);
            logger.setInputValue("log", tree);

            String fileName = fileNameStem + ".trees";

            if (multipleTrees) {
                fileName = fileNameStem + "_" + tree.getID() + ".trees";
            }

            if (fileNameStem != null) logger.setInputValue("fileName", fileName);
            logger.initAndValidate();
            logger.setID(tree.getID() + ".treeLogger");
            treeLoggers.add(logger);
            elements.add(logger);
        }
        return treeLoggers;
    }

    private Logger createScreenLogger(int logEvery) {
        return createLogger(logEvery, null);
    }

    public static double getOperatorWeight(int size) {
        return Math.pow(size, 0.7);
    }

    private Operator createTreeScaleOperator(Tree tree) {
        ScaleOperator operator = new ScaleOperator();
        operator.setInputValue("tree", tree);
        operator.setInputValue("weight", getOperatorWeight(tree.getInternalNodeCount()));
        operator.initAndValidate();
        operator.setID(tree.getID() + "." + "scale");
        elements.add(operator);

        return operator;
    }

    private Operator createTreeUniformOperator(Tree tree) {
        Uniform uniform = new Uniform();
        uniform.setInputValue("tree", tree);
        uniform.setInputValue("weight", getOperatorWeight(tree.getInternalNodeCount()));
        uniform.initAndValidate();
        uniform.setID(tree.getID() + "." + "uniform");
        elements.add(uniform);

        return uniform;
    }

    private Operator createSubtreeSlideOperator(Tree tree) {
        SubtreeSlide subtreeSlide = new SubtreeSlide();
        subtreeSlide.setInputValue("tree", tree);
        subtreeSlide.setInputValue("weight", getOperatorWeight(tree.getInternalNodeCount()));
        subtreeSlide.setInputValue("size", tree.getRoot().getHeight() / 10.0);
        subtreeSlide.initAndValidate();
        subtreeSlide.setID(tree.getID() + "." + "subtreeSlide");
        elements.add(subtreeSlide);

        return subtreeSlide;
    }

    private Operator createExchangeOperator(Tree tree, boolean isNarrow) {
        Exchange exchange = new Exchange();
        exchange.setInputValue("tree", tree);
        exchange.setInputValue("weight", getOperatorWeight(tree.getInternalNodeCount()));
        exchange.setInputValue("isNarrow", isNarrow);
        exchange.initAndValidate();
        exchange.setID(tree.getID() + "." + ((isNarrow) ? "narrow" : "wide") + "Exchange");
        elements.add(exchange);

        return exchange;
    }

    private Operator createBEASTOperator(RealParameter parameter) {
        RandomVariable<?> variable = (RandomVariable<?>) BEASTToLPHYMap.get(parameter);

        Operator operator;
        if (variable != null && variable.getGenerativeDistribution() instanceof Dirichlet) {
            Double[] value = (Double[]) variable.value();
            operator = new DeltaExchangeOperator();
            operator.setInputValue("parameter", parameter);
            operator.setInputValue("weight", getOperatorWeight(parameter.getDimension() - 1));
            operator.setInputValue("delta", 1.0 / value.length);
            operator.initAndValidate();
            operator.setID(parameter.getID() + ".deltaExchange");
        } else {
            operator = new ScaleOperator();
            operator.setInputValue("parameter", parameter);
            operator.setInputValue("weight", getOperatorWeight(parameter.getDimension()));
            operator.setInputValue("scaleFactor", 0.75);
            operator.initAndValidate();
            operator.setID(parameter.getID() + ".scale");
        }
        elements.add(operator);

        return operator;
    }

    private Operator createBEASTOperator(BooleanParameter parameter) {
        Operator operator = new BitFlipOperator();
        operator.setInputValue("parameter", parameter);
        operator.setInputValue("weight", getOperatorWeight(parameter.getDimension()));
        operator.initAndValidate();
        operator.setID(parameter.getID() + ".bitFlip");

        return operator;
    }

    private Operator createBEASTOperator(IntegerParameter parameter) {
        RandomVariable<?> variable = (RandomVariable<?>) BEASTToLPHYMap.get(parameter);

        Operator operator;
        if (variable.getGenerativeDistribution() instanceof RandomComposition) {
            System.out.println("Constructing operator for randomComposition");
            operator = new DeltaExchangeOperator();
            operator.setInputValue("intparameter", parameter);
            operator.setInputValue("weight", getOperatorWeight(parameter.getDimension() - 1));
            operator.setInputValue("delta", 2.0);
            operator.setInputValue("integer", true);
            operator.initAndValidate();
            operator.setID(parameter.getID() + ".deltaExchange");
        } else {
            operator = new IntRandomWalkOperator();
            operator.setInputValue("parameter", parameter);
            operator.setInputValue("weight", getOperatorWeight(parameter.getDimension()));

            // TODO implement an optimizable int random walk that uses a reflected Poisson distribution for the jump size with the mean of the Poisson being the optimizable parameter
            operator.setInputValue("windowSize", 1);
            operator.initAndValidate();
            operator.setID(parameter.getID() + ".randomWalk");
        }
        elements.add(operator);
        return operator;
    }

    private CompoundDistribution createBEASTPosterior() {

        List<Distribution> priorList = new ArrayList<>();

        List<Distribution> likelihoodList = new ArrayList<>();

        for (Map.Entry<GraphicalModelNode<?>, BEASTInterface> entry : beastObjects.entrySet()) {
            if (entry.getValue() instanceof Distribution) {
                GenerativeDistribution g = (GenerativeDistribution) entry.getKey();

                if (generatorOfSink(g)) {
                    likelihoodList.add((Distribution) entry.getValue());
                } else {
                    priorList.add((Distribution) entry.getValue());
                }
            }
        }

        for (BEASTInterface beastInterface : elements) {
            if (beastInterface instanceof Distribution && !likelihoodList.contains(beastInterface) && !priorList.contains(beastInterface)) {
                priorList.add((Distribution) beastInterface);
            }
        }

        System.out.println("Found " + likelihoodList.size() + " likelihoods.");
        System.out.println("Found " + priorList.size() + " priors.");

        CompoundDistribution priors = new CompoundDistribution();
        priors.setInputValue("distribution", priorList);
        priors.initAndValidate();
        priors.setID("prior");
        elements.add(priors);

        CompoundDistribution likelihoods = new CompoundDistribution();
        likelihoods.setInputValue("distribution", likelihoodList);
        likelihoods.initAndValidate();
        likelihoods.setID("likelihood");
        elements.add(likelihoods);

        List<Distribution> posteriorList = new ArrayList<>();
        posteriorList.add(priors);
        posteriorList.add(likelihoods);

        CompoundDistribution posterior = new CompoundDistribution();
        posterior.setInputValue("distribution", posteriorList);
        posterior.initAndValidate();
        posterior.setID("posterior");
        elements.add(posterior);

        return posterior;
    }

    private boolean generatorOfSink(GenerativeDistribution g) {
        for (Value<?> var : parser.getModelSinks()) {
            if (var.getGenerator() == g) {
                return true;
            }
        }
        return false;
    }

    public MCMC createMCMC(long chainLength, int logEvery, String fileName) {

        createBEASTObjects();

        CompoundDistribution posterior = createBEASTPosterior();

        MCMC mcmc = new MCMC();
        mcmc.setInputValue("distribution", posterior);
        mcmc.setInputValue("chainLength", chainLength);

        List<Operator> operators = createOperators();
        for (int i = 0; i < operators.size(); i++) {
            System.out.println(operators.get(i));
        }

        mcmc.setInputValue("operator", operators);
        mcmc.setInputValue("logger", createLoggers(logEvery, fileName));

        State state = new State();
        state.setInputValue("stateNode", this.state);
        state.initAndValidate();
        elements.add(state);

        // TODO make sure the stateNode list is being correctly populated
        mcmc.setInputValue("state", state);

        if (inits.size() > 0) mcmc.setInputValue("init", inits);

        mcmc.initAndValidate();
        return mcmc;
    }

    public void clear() {
        state.clear();
        elements.clear();
        beastObjects.clear();
        extraOperators.clear();
    }

    public void runBEAST(String fileNameStem) {

        MCMC mcmc = createMCMC(1000000, 1000, fileNameStem);

        try {
            mcmc.run();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    public String toBEASTXML(String fileNameStem) {

        MCMC mcmc = createMCMC(1000000, 1000, fileNameStem);

        String xml = new XMLProducer().toXML(mcmc, elements);

        return xml;
    }

    public void addExtraOperator(Operator operator) {
        extraOperators.add(operator);
    }

    public void addTaxon(String taxonID) {
        if (!allTaxa.containsKey(taxonID)) {
            allTaxa.put(taxonID, new Taxon(taxonID));
        }
    }

    /**
     * @param id
     * @return the taxon with this id.
     */
    public Taxon getTaxon(String id) {
        addTaxon(id);
        return allTaxa.get(id);
    }

    public List<Taxon> createTaxonList(List<String> ids) {
        List<Taxon> taxonList = new ArrayList<>();
        for (String id : ids) {
            Taxon taxon = allTaxa.get(id);
            if (taxon == null) {
                addTaxon(id);
                taxonList.add(allTaxa.get(id));
            } else {
                taxonList.add(taxon);
            }
        }
        return taxonList;
    }

    public void putBEASTObject(GraphicalModelNode node, BEASTInterface beastInterface) {
        addToContext(node,beastInterface);
    }

    public void addExtraLogger(Loggable loggable) {
        extraLoggables.add(loggable);
    }

    public void addInit(StateNodeInitialiser beastInitializer) {
        inits.add(beastInitializer);
    }

    public List<Value<lphy.evolution.alignment.Alignment>> getAlignments() {
        ArrayList<Value<lphy.evolution.alignment.Alignment>> alignments = new ArrayList<>();
        for (GraphicalModelNode node : beastObjects.keySet()) {
            if (node instanceof Value && node.value() instanceof lphy.evolution.alignment.Alignment) {
                alignments.add((Value<lphy.evolution.alignment.Alignment>)node);
            }
        }
        return alignments;
    }
}