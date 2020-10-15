package lphybeast.tobeast.values;

import beast.core.parameter.RealParameter;
import lphy.core.distributions.Dirichlet;
import lphy.core.distributions.LogNormalMulti;
import lphy.graphicalModel.Value;
import lphybeast.BEASTContext;
import lphybeast.ValueToBEAST;
import outercore.parameter.KeyRealParameter;

import java.util.Arrays;
import java.util.List;

public class DoubleArrayValueToBEAST implements ValueToBEAST<Double[], KeyRealParameter> {

    @Override
    public KeyRealParameter valueToBEAST(Value<Double[]> value, BEASTContext context) {

        KeyRealParameter parameter = new KeyRealParameter();
        List<Number> values = Arrays.asList(value.value());
        parameter.setInputValue("value", values);
        parameter.setInputValue("dimension", values.size());

        // check domain
        if (value.getGenerator() instanceof Dirichlet) {
            parameter.setInputValue("lower", 0.0);
            parameter.setInputValue("upper", 1.0);
        } else if (value.getGenerator() instanceof LogNormalMulti) {
            parameter.setInputValue("lower", 0.0);
        }

        parameter.initAndValidate();
        ValueToParameter.setID(parameter, value);
        return parameter;
    }

    @Override
    public Class getValueClass() {
        return Double[].class;
    }

    @Override
    public Class<KeyRealParameter> getBEASTClass() {
        return KeyRealParameter.class;
    }

}
