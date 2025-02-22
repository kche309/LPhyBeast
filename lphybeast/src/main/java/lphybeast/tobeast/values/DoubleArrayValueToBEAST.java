package lphybeast.tobeast.values;

import beast.core.BEASTInterface;
import beast.core.Function;
import beast.core.Operator;
import beast.core.parameter.IntegerParameter;
import beast.core.parameter.Parameter;
import beast.core.parameter.RealParameter;
import beast.evolution.operators.DeltaExchangeOperator;
import feast.function.Concatenate;
import lphy.core.distributions.Dirichlet;
import lphy.core.distributions.WeightedDirichlet;
import lphy.graphicalModel.Value;
import lphy.graphicalModel.VectorUtils;
import lphybeast.BEASTContext;
import lphybeast.ValueToBEAST;

import java.util.ArrayList;
import java.util.List;

public class DoubleArrayValueToBEAST implements ValueToBEAST<Double[], BEASTInterface> {

    @Override
    public BEASTInterface valueToBEAST(Value<Double[]> value, BEASTContext context) {

        if (value.getGenerator() instanceof WeightedDirichlet) {

            Concatenate concatenatedParameters = new Concatenate();
            Double[] values = value.value();

            List<Function> args = new ArrayList<>();
            for (int i = 0; i < values.length; i++) {
                RealParameter parameter = BEASTContext.createRealParameter(value.getCanonicalId() + VectorUtils.INDEX_SEPARATOR + i, values[i]);
                context.addStateNode(parameter, value, false);
                args.add(parameter);
            }
            concatenatedParameters.setInputValue("arg", args);
            concatenatedParameters.initAndValidate();

            ValueToParameter.setID(concatenatedParameters, value);

            addDeltaExchangeOperator(value, args, context);

            return concatenatedParameters;
        }

        Double lower = null;
        Double upper = null;
        // check domain
        if (value.getGenerator() instanceof Dirichlet) {
            lower = 0.0;
            upper = 1.0;
        } else if (value.getGenerator() instanceof WeightedDirichlet) {
            lower = 0.0;
//        } else if (value.getGenerator() instanceof LogNormalMulti) {
//            lower = 0.0;
        }

        Parameter parameter = BEASTContext.createParameterWithBound(value, lower, upper, false);
        if (!(parameter instanceof RealParameter))
            throw new IllegalStateException("Expecting to create KeyRealParameter from " + value.getCanonicalId());

        return (RealParameter) parameter;

    }

    @Override
    public Class getValueClass() {
        return Double[].class;
    }

    @Override
    public Class<BEASTInterface> getBEASTClass() {
        return BEASTInterface.class;
    }

    private void addDeltaExchangeOperator(Value<Double[]> value, List<Function> args, BEASTContext context) {
        WeightedDirichlet weightedDirichlet = (WeightedDirichlet) value.getGenerator();
        IntegerParameter weightIntParam = context.getAsIntegerParameter(weightedDirichlet.getWeights());

        Operator operator = new DeltaExchangeOperator();
        operator.setInputValue("parameter", args);
        operator.setInputValue("weight", BEASTContext.getOperatorWeight(args.size() - 1));
        operator.setInputValue("weightvector", weightIntParam);
        operator.setInputValue("delta", 1.0 / value.value().length);
        operator.initAndValidate();
        operator.setID(value.getCanonicalId() + ".deltaExchange");
        context.addExtraOperator(operator);
    }
}
