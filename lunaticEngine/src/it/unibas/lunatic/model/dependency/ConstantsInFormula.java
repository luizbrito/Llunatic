package it.unibas.lunatic.model.dependency;

import it.unibas.lunatic.model.algebra.operators.StringComparator;
import it.unibas.lunatic.utility.DependencyUtility;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConstantsInFormula {
    
    private Dependency dependency;
    private Map<String, ConstantInFormula> constantMap = new HashMap<String, ConstantInFormula>();
    private String tableName;

    public ConstantsInFormula(Dependency dependency) {
        this.dependency = dependency;
    }

    public Dependency getDependency() {
        return dependency;
    }

    public Map<String, ConstantInFormula> getConstantMap() {
        return constantMap;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getOrderedKeys() {
        List<String> orderedKeys = new ArrayList<String>(constantMap.keySet());
        Collections.sort(orderedKeys, new StringComparator()); 
        return orderedKeys;
    }
    
    public List<String> getAttributeNames() {
        List<String> result = new ArrayList<String>();
        for (String key : getOrderedKeys()) {
            result.add(DependencyUtility.buildAttributeNameForConstant(constantMap.get(key).getConstantValue()));
        }
        return result;
    }

    public List<Object> getConstantValues() {
        List<Object> result = new ArrayList<Object>();
        for (String key : getOrderedKeys()) {
            result.add(constantMap.get(key).getConstantValue());
        }
        return result;
    }
    
    public boolean isEmpty() {
        return this.constantMap.isEmpty();
    }

    @Override
    public String toString() {
        return "ConstantsInFormula{" + "dependency=" + dependency + ", constantMap=" + constantMap + ", tableName=" + tableName + '}';
    }    

}
