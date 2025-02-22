package lphybeast.tobeast.generators;

import beast.core.BEASTInterface;
import beast.core.parameter.RealParameter;
import beast.evolution.alignment.AlignmentFromTrait;
import beast.evolution.branchratemodel.StrictClockModel;
import beast.evolution.branchratemodel.UCRelaxedClockModel;
import beast.evolution.datatype.DataType;
import beast.evolution.datatype.UserDataType;
import beast.evolution.likelihood.AncestralStateTreeLikelihood;
import beast.evolution.likelihood.GenericTreeLikelihood;
import beast.evolution.likelihood.ThreadedTreeLikelihood;
import beast.evolution.operators.UpDownOperator;
import beast.evolution.sitemodel.SiteModel;
import beast.evolution.substitutionmodel.SVSGeneralSubstitutionModelLogger;
import beast.evolution.substitutionmodel.SubstitutionModel;
import beast.evolution.tree.Tree;
import beast.math.distributions.Prior;
import consoperators.BigPulley;
import consoperators.InConstantDistanceOperator;
import consoperators.SimpleDistance;
import consoperators.SmallPulley;
import lphy.core.distributions.DiscretizedGamma;
import lphy.core.distributions.IID;
import lphy.core.distributions.LogNormal;
import lphy.evolution.branchrates.LocalBranchRates;
import lphy.evolution.datatype.SequenceTypeFactory;
import lphy.evolution.likelihood.PhyloCTMC;
import lphy.evolution.substitutionmodel.RateMatrix;
import lphy.evolution.tree.TimeTree;
import lphy.graphicalModel.Generator;
import lphy.graphicalModel.RandomVariable;
import lphy.graphicalModel.Value;
import lphybeast.BEASTContext;
import lphybeast.GeneratorToBEAST;

public class PhyloCTMCToBEAST implements GeneratorToBEAST<PhyloCTMC, GenericTreeLikelihood> {

    private static final String LOCATION = "location";

    public GenericTreeLikelihood generatorToBEAST(PhyloCTMC phyloCTMC, BEASTInterface value, BEASTContext context) {

        if (SequenceTypeFactory.INSTANCE.isStandardDataType( phyloCTMC.getDataType() ) ) { // TODO morphology
            // for discrete phylogeography
            return createAncestralStateTreeLikelihood(phyloCTMC, value, context);
        } else {
            return createThreadedTreeLikelihood(phyloCTMC, value, context);
        }

    }

    private AncestralStateTreeLikelihood createAncestralStateTreeLikelihood(PhyloCTMC phyloCTMC, BEASTInterface value, BEASTContext context) {
        AncestralStateTreeLikelihood treeLikelihood = new AncestralStateTreeLikelihood();
        treeLikelihood.setInputValue("tag", LOCATION);

        assert value instanceof beast.evolution.alignment.AlignmentFromTrait;
        AlignmentFromTrait traitAlignment = (beast.evolution.alignment.AlignmentFromTrait)value;
        treeLikelihood.setInputValue("data", traitAlignment);

        constructTreeAndBranchRate(phyloCTMC, treeLikelihood, context);

        DataType userDataType = traitAlignment.getDataType();
        if (! (userDataType instanceof UserDataType) )
            throw new IllegalArgumentException("Substitution Model was null!");

        SiteModel siteModel = constructGeoSiteModel(phyloCTMC, context, (UserDataType) userDataType);
        treeLikelihood.setInputValue("siteModel", siteModel);

        treeLikelihood.initAndValidate();
        treeLikelihood.setID(traitAlignment.getID() + ".treeLikelihood");
        // logging
        context.addExtraLogger(treeLikelihood);

        return treeLikelihood;
    }

    private SiteModel constructGeoSiteModel(PhyloCTMC phyloCTMC, BEASTContext context, UserDataType userDataType) {
        SiteModel siteModel = new SiteModel();

        Value<Double[]> siteRates = phyloCTMC.getSiteRates();
        // only 1 site
        if (siteRates == null) {
            siteModel.setInputValue("gammaCategoryCount", 1);
        } else {
            throw new UnsupportedOperationException("Discrete traits will only have 1 site !");
        }

        Generator qGenerator = phyloCTMC.getQ().getGenerator();
        if (qGenerator == null || !(qGenerator instanceof RateMatrix)) {
            throw new RuntimeException("BEAST2 only supports Q matrices constructed by a RateMatrix function.");
        } else {
            RateMatrix rateMatrix = (RateMatrix)qGenerator;

            BEASTInterface mutationRate = context.getBEASTObject(rateMatrix.getMeanRate());

            SubstitutionModel substitutionModel = (SubstitutionModel) context.getBEASTObject(qGenerator);

            if (substitutionModel == null) throw new IllegalArgumentException("Substitution Model was null!");

            siteModel.setInputValue("substModel", substitutionModel);
            if (mutationRate != null) siteModel.setInputValue("mutationRate", mutationRate);
            siteModel.initAndValidate();

            // add SVSGeneralSubstitutionModelLogger
            SVSGeneralSubstitutionModelLogger svsLogger = new SVSGeneralSubstitutionModelLogger();
            svsLogger.setInputValue("dataType", userDataType);
            svsLogger.setInputValue("model", substitutionModel);
            svsLogger.initAndValidate();

            if (svsLogger.getID() == null)
                svsLogger.setID(svsLogger.toString().substring(0, 3));

            context.addExtraLogger(svsLogger);
        }
        siteModel.setID("geo." + siteModel.toString());
        return siteModel;
    }


    private ThreadedTreeLikelihood createThreadedTreeLikelihood(PhyloCTMC phyloCTMC, BEASTInterface value, BEASTContext context) {
        ThreadedTreeLikelihood treeLikelihood = new ThreadedTreeLikelihood();

        assert value instanceof beast.evolution.alignment.Alignment;
        beast.evolution.alignment.Alignment alignment = (beast.evolution.alignment.Alignment)value;
        treeLikelihood.setInputValue("data", alignment);

        constructTreeAndBranchRate(phyloCTMC, treeLikelihood, context);

        SiteModel siteModel = constructSiteModel(phyloCTMC, context);
        treeLikelihood.setInputValue("siteModel", siteModel);

        treeLikelihood.initAndValidate();
        treeLikelihood.setID(alignment.getID() + ".treeLikelihood");
        // logging
        context.addExtraLogger(treeLikelihood);

        return treeLikelihood;
    }


    /**
     * Create tree and clock rate inside this tree likelihood.
     * @param phyloCTMC
     * @param treeLikelihood
     * @param context
     */
    public static void constructTreeAndBranchRate(PhyloCTMC phyloCTMC, GenericTreeLikelihood treeLikelihood, BEASTContext context) {
        constructTreeAndBranchRate(phyloCTMC, treeLikelihood, context, false);
    }

    /**
     * Create tree and clock rate inside this tree likelihood.
     * @param phyloCTMC
     * @param treeLikelihood
     * @param context
     * @param skipBranchOperators skip constructing branch rates
     */
    public static void constructTreeAndBranchRate(PhyloCTMC phyloCTMC, GenericTreeLikelihood treeLikelihood, BEASTContext context, boolean skipBranchOperators) {
        Value<TimeTree> timeTreeValue = phyloCTMC.getTree();
        Tree tree = (Tree) context.getBEASTObject(timeTreeValue);
        //tree.setInputValue("taxa", value);
        //tree.initAndValidate();

        treeLikelihood.setInputValue("tree", tree);

        Value<Double[]> branchRates = phyloCTMC.getBranchRates();

        if (branchRates != null) {

            Generator generator = branchRates.getGenerator();
            if (generator instanceof IID &&
                    ((IID<?>) generator).getBaseDistribution() instanceof LogNormal) {

                // simpleRelaxedClock.lphy
                UCRelaxedClockModel relaxedClockModel = new UCRelaxedClockModel();

                Prior logNormalPrior = (Prior) context.getBEASTObject(generator);

                RealParameter beastBranchRates = (RealParameter) context.getBEASTObject(branchRates);

                relaxedClockModel.setInputValue("rates", beastBranchRates);
                relaxedClockModel.setInputValue("tree", tree);
                relaxedClockModel.setInputValue("distr", logNormalPrior.distInput.get());
                relaxedClockModel.setID(branchRates.getCanonicalId() + ".model");
                relaxedClockModel.initAndValidate();
                treeLikelihood.setInputValue("branchRateModel", relaxedClockModel);

                if (skipBranchOperators == false) {
                    addRelaxedClockOperators(tree, relaxedClockModel, beastBranchRates, context);
                }

            } else if (generator instanceof LocalBranchRates) {
                treeLikelihood.setInputValue("branchRateModel", context.getBEASTObject(generator));
            } else {
                throw new RuntimeException("Only localBranchRates and lognormally distributed branchRates currently supported for BEAST2 conversion");
            }

            if (branchRates instanceof RandomVariable && timeTreeValue instanceof RandomVariable) {
//                throw new UnsupportedOperationException("in development");
            }

        } else {
            StrictClockModel clockModel = new StrictClockModel();
            Value<Number> clockRate = phyloCTMC.getClockRate();

            RealParameter clockRatePara;
            if (clockRate != null) {
                clockRatePara = (RealParameter) context.getBEASTObject(clockRate);

            } else {
                clockRatePara =  BEASTContext.createRealParameter(1.0);
            }
            clockModel.setInputValue("clock.rate", clockRatePara);
            treeLikelihood.setInputValue("branchRateModel", clockModel);

            if (clockRate instanceof RandomVariable && timeTreeValue instanceof RandomVariable && skipBranchOperators == false) {
                addUpDownOperator(tree, clockRatePara, context);
            }
        }
    }


    /**
     * @param phyloCTMC the phyloCTMC object
     * @param context the beast context
     * @return a BEAST SiteModel representing the site model of this LPHY PhyloCTMC
     */
    public static SiteModel constructSiteModel(PhyloCTMC phyloCTMC, BEASTContext context) {

        SiteModel siteModel = new SiteModel();

        Value<Double[]> siteRates = phyloCTMC.getSiteRates();

        if (siteRates != null) {
            Generator generator = siteRates.getGenerator();

            DiscretizedGamma discretizedGamma;
            if (generator instanceof DiscretizedGamma) {
                discretizedGamma = (DiscretizedGamma)generator;
            } else if (generator instanceof IID) {
                discretizedGamma = (DiscretizedGamma) ((IID)generator).getBaseDistribution();
            } else {
                throw new RuntimeException("Only discretized gamma site rates are supported by LPhyBEAST");
            }
            siteModel.setInputValue("shape", context.getAsRealParameter(discretizedGamma.getShape()));
            siteModel.setInputValue("gammaCategoryCount", discretizedGamma.getNcat().value());

            //TODO need a better solution than rm RandomVariable siteRates
            context.removeBEASTObject(context.getBEASTObject(siteRates));
        }

        Generator qGenerator = phyloCTMC.getQ().getGenerator();
        if (qGenerator == null || !(qGenerator instanceof RateMatrix)) {
            throw new RuntimeException("BEAST2 only supports Q matrices constructed by a RateMatrix function (e.g. hky, gtr, jukeCantor et cetera).");
        } else {
            SubstitutionModel substitutionModel = (SubstitutionModel) context.getBEASTObject(qGenerator);
            if (substitutionModel == null) throw new IllegalArgumentException("Substitution Model was null!");
            siteModel.setInputValue("substModel", substitutionModel);

            RateMatrix rateMatrix = (RateMatrix)qGenerator;
            Value<Double> meanRate = rateMatrix.getMeanRate();
            BEASTInterface mutationRate = meanRate==null ? null : context.getBEASTObject(meanRate);
            if (mutationRate != null) siteModel.setInputValue("mutationRate", mutationRate);

            siteModel.initAndValidate();
        }
        return siteModel;
    }

    private static void addRelaxedClockOperators(Tree tree, UCRelaxedClockModel relaxedClockModel, RealParameter rates, BEASTContext context) {

        double tWindowSize = tree.getRoot().getHeight() / 10.0;

        InConstantDistanceOperator inConstantDistanceOperator = new InConstantDistanceOperator();
        inConstantDistanceOperator.setInputValue("clockModel", relaxedClockModel);
        inConstantDistanceOperator.setInputValue("tree", tree);
        inConstantDistanceOperator.setInputValue("rates", rates);
        inConstantDistanceOperator.setInputValue("twindowSize", tWindowSize);
        inConstantDistanceOperator.setInputValue("weight", BEASTContext.getOperatorWeight(tree.getNodeCount()));
        inConstantDistanceOperator.setID(relaxedClockModel.getID() + ".inConstantDistanceOperator");
        inConstantDistanceOperator.initAndValidate();
        context.addExtraOperator(inConstantDistanceOperator);

        SimpleDistance simpleDistance = new SimpleDistance();
        simpleDistance.setInputValue("clockModel", relaxedClockModel);
        simpleDistance.setInputValue("tree", tree);
        simpleDistance.setInputValue("rates", rates);
        simpleDistance.setInputValue("twindowSize", tWindowSize);
        simpleDistance.setInputValue("weight", BEASTContext.getOperatorWeight(2));
        simpleDistance.setID(relaxedClockModel.getID() + ".simpleDistance");
        simpleDistance.initAndValidate();
        context.addExtraOperator(simpleDistance);

        BigPulley bigPulley = new BigPulley();
        bigPulley.setInputValue("tree", tree);
        bigPulley.setInputValue("rates", rates);
        bigPulley.setInputValue("twindowSize", tWindowSize);
        bigPulley.setInputValue("dwindowSize", 0.1);
        bigPulley.setInputValue("weight", BEASTContext.getOperatorWeight(2));
        bigPulley.setID(relaxedClockModel.getID() + ".bigPulley");
        bigPulley.initAndValidate();
        context.addExtraOperator(bigPulley);

        SmallPulley smallPulley = new SmallPulley();
        smallPulley.setInputValue("clockModel", relaxedClockModel);
        smallPulley.setInputValue("tree", tree);
        smallPulley.setInputValue("rates", rates);
        smallPulley.setInputValue("dwindowSize", 0.1);
        smallPulley.setInputValue("weight", BEASTContext.getOperatorWeight(2));
        smallPulley.setID(relaxedClockModel.getID() + ".smallPulley");
        smallPulley.initAndValidate();
        context.addExtraOperator(smallPulley);
    }

    // when both mu and tree are random var
    private static void addUpDownOperator(Tree tree, RealParameter clockRate, BEASTContext context) {
        String idStr = clockRate.getID() + "Up" + tree.getID() + "DownOperator";
        // avoid to duplicate updown ops from the same pair of rate and tree
        if (!context.hasExtraOperator(idStr)) {
            UpDownOperator upDownOperator = new UpDownOperator();
            upDownOperator.setID(idStr);
            upDownOperator.setInputValue("up", clockRate);
            upDownOperator.setInputValue("down", tree);
            upDownOperator.setInputValue("scaleFactor", 0.9);
            upDownOperator.setInputValue("weight", BEASTContext.getOperatorWeight(tree.getInternalNodeCount()+1));
            context.addExtraOperator(upDownOperator);
        }
    }

    @Override
    public Class<PhyloCTMC> getGeneratorClass() {
        return PhyloCTMC.class;
    }

    @Override
    public Class<GenericTreeLikelihood> getBEASTClass() {
        return GenericTreeLikelihood.class;
    }
}
