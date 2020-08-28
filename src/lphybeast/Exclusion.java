package lphybeast;

import lphy.core.functions.DataFrameConstruction;
import lphy.core.functions.NTaxaFunction;
import lphy.core.functions.Nexus;
import lphy.evolution.DataFrame;
import lphy.graphicalModel.Generator;
import lphy.graphicalModel.Value;

/**
 * Utils class to exclude {@link lphy.graphicalModel.Value}
 * or {@link lphy.graphicalModel.Generator}.
 * @author Walter Xie
 */
public class Exclusion {

    public static boolean isExcludedValue(Value<?> val) {
        // ignore all String: d = nexus(file="Dengue4.nex");
        return ((val.value() instanceof String) || (val.value() instanceof DataFrame));
    }

    public static boolean isExcludedGenerator(Generator generator) {
        return ((generator instanceof NTaxaFunction) || (generator instanceof DataFrameConstruction) ||
                (generator instanceof Nexus));
    }
}
