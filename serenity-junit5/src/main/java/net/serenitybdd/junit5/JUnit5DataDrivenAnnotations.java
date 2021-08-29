package net.serenitybdd.junit5;

import au.com.bytecode.opencsv.CSVReader;
import com.google.common.base.Splitter;
import net.serenitybdd.junit5.datadriven.JUnit5CSVTestDataSource;
import net.thucydides.core.guice.Injectors;
import net.thucydides.core.model.DataTable;
import net.thucydides.core.util.EnvironmentVariables;
import org.apache.commons.collections.SetUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

public class JUnit5DataDrivenAnnotations {


    private final Logger logger = LoggerFactory.getLogger(JUnit5DataDrivenAnnotations.class);

    private final EnvironmentVariables environmentVariables;

    private final Class testClass;

    private final Map<String,DataTable> parameterTables;

    public static JUnit5DataDrivenAnnotations forClass(final Class testClass) {
        return new JUnit5DataDrivenAnnotations(testClass);
    }

    JUnit5DataDrivenAnnotations(final Class testClass) {
        this(testClass, Injectors.getInjector().getProvider(EnvironmentVariables.class).get());
    }

    JUnit5DataDrivenAnnotations(final Class testClass, EnvironmentVariables environmentVariables) {
        this.testClass = testClass;
        this.environmentVariables = environmentVariables;
        this.parameterTables = generateParameterTables();
    }

    public Map<String,DataTable> getParameterTables() {
        return parameterTables;
    }

    private Map<String,DataTable> generateParameterTables() {
        List<Method> allMethods = findTestDataMethods();
        Map<String,DataTable> dataTables = new HashMap<>();
        for(Method testDataMethod : allMethods) {
            if(isAValueSourceAnnotatedMethod(testDataMethod)) {
                fillDataTablesFromValueSource(dataTables, testDataMethod);
            }
            else if(isACsvFileSourceAnnotatedMethod(testDataMethod))
            {
                fillDataTablesFromCsvFileSource(dataTables,testDataMethod);
            }
            else if (isAEnumSourceAnnotatedMethod(testDataMethod)) {
                fillDataTablesFromEnumSource(dataTables,testDataMethod);
            }
            else if (isACsvSourceAnnotatedMethod(testDataMethod)) {
                fillDataTablesFromCsvSource(dataTables,testDataMethod);
            }
        }
        return dataTables;
    }

    private void fillDataTablesFromEnumSource(Map<String, DataTable> dataTables, Method testDataMethod) {
        String columnNamesString = createColumnNamesFromParameterNames(testDataMethod);
        String dataTableName = testClass.getCanonicalName() + "." + testDataMethod.getName();
        List<List<Object>> parametersAsListsOfObjects = listOfEnumSourceObjectsFrom(testDataMethod);
        logger.info("GetParameterTablesEnumSource: Put parameter dataTableName " + dataTableName + " -- " + parametersAsListsOfObjects);
        dataTables.put(dataTableName, createParametersTableFrom(columnNamesString, parametersAsListsOfObjects));
    }

    private void fillDataTablesFromValueSource(Map<String, DataTable> dataTables, Method testDataMethod) {
        String columnNamesString = createColumnNamesFromParameterNames(testDataMethod);
        String dataTableName = testClass.getCanonicalName() + "." + testDataMethod.getName();
        List<List<Object>> parametersAsListsOfObjects = listOfObjectsFrom(testDataMethod);
        logger.info("GetParameterTables: Put parameter dataTableName " + dataTableName + " -- " + parametersAsListsOfObjects);
        dataTables.put(dataTableName, createParametersTableFrom(columnNamesString, parametersAsListsOfObjects));
    }

    private void fillDataTablesFromCsvSource(Map<String, DataTable> dataTables, Method testDataMethod) {
        String columnNamesString = createColumnNamesFromParameterNames(testDataMethod);
        String dataTableName = testClass.getCanonicalName() + "." + testDataMethod.getName();
        List<List<Object>> parametersAsListsOfObjects = listOfCsvObjectsFrom(testDataMethod);
        logger.info("GetParameterTables: Put parameter dataTableName " + dataTableName + " -- " + parametersAsListsOfObjects);
        dataTables.put(dataTableName, createParametersTableFrom(columnNamesString, parametersAsListsOfObjects));
    }

    private void fillDataTablesFromCsvFileSource(Map<String, DataTable> dataTables, Method testDataMethod) {
        CsvFileSource annotation = testDataMethod.getAnnotation(CsvFileSource.class);
        String columnNamesString = createColumnNamesFromParameterNames(testDataMethod);
        String dataTableName = testClass.getCanonicalName() + "." + testDataMethod.getName();
        try {
            JUnit5CSVTestDataSource csvTestDataSource = new JUnit5CSVTestDataSource(Arrays.asList(annotation.resources()), CSVReader.DEFAULT_SEPARATOR);
            List<Map<String, String>> data = csvTestDataSource.getData();
            List<List<Object>> rows  = new ArrayList<>();
            for(Map<String,String> dataRowMap : data)
            {
                ArrayList<Object> dataRow = new ArrayList<>();
                for(String header : csvTestDataSource.getHeaders()) {
                    dataRow.add(dataRowMap.get(header));
                }
                rows.add(dataRow);
            }
            logger.info("GetParameterTablesCSV: Put parameter dataTableName " + dataTableName);
            dataTables.put(dataTableName, createParametersTableFrom(columnNamesString,rows));
        } catch (IOException e) {
            logger.error("Cannot load csv resource ",e);
        }
    }

    List<Method> findTestDataMethods() {
        return Arrays.asList(testClass.getMethods()).stream().filter(this::findParameterizedTests).collect(Collectors.toList());
    }

    String createColumnNamesFromParameterNames(Method method) {
        return Arrays.asList(method.getParameters()).stream().map(Parameter::getName).collect(Collectors.joining(","));
    }

    List<List<Object>> listOfObjectsFrom(Method testDataMethod){
        ValueSource annotation = testDataMethod.getAnnotation(ValueSource.class);
        if(ArrayUtils.isNotEmpty(annotation.strings()))
            return listOfObjectsFrom(annotation.strings());
        else if(ArrayUtils.isNotEmpty(annotation.bytes()))
            return listOfObjectsFrom(ArrayUtils.toObject(annotation.bytes()));
        else if(ArrayUtils.isNotEmpty(annotation.chars()))
            return listOfObjectsFrom(ArrayUtils.toObject(annotation.chars()));
        else if(ArrayUtils.isNotEmpty(annotation.doubles()))
            return listOfObjectsFrom(ArrayUtils.toObject(annotation.doubles()));
        else if(ArrayUtils.isNotEmpty(annotation.floats()))
            return listOfObjectsFrom(ArrayUtils.toObject(annotation.floats()));
        else if(ArrayUtils.isNotEmpty(annotation.ints()))
            return listOfObjectsFrom(ArrayUtils.toObject(annotation.ints()));
        else if(ArrayUtils.isNotEmpty(annotation.shorts()))
            return listOfObjectsFrom(ArrayUtils.toObject(annotation.shorts()));
        else if(ArrayUtils.isNotEmpty(annotation.classes()))
            return listOfObjectsFrom(annotation.classes());
        return null;
    }

    List<List<Object>> listOfCsvObjectsFrom(Method testDataMethod){
        CsvSource annotation = testDataMethod.getAnnotation(CsvSource.class);
        String annotationDelimiter = annotation.delimiterString();
        String delimiter = (annotationDelimiter != null && !annotationDelimiter.isEmpty()) ? annotationDelimiter : ",";
        return listOfCsvObjectsFrom(annotation.value(), delimiter);
    }

    private List<List<Object>> listOfCsvObjectsFrom(Object[] parameters,String delimiter) {
        List<List<Object>> ret = new ArrayList<>();
        for(Object parameter : parameters) {
            String[] split = ((String) parameter).split(delimiter);
            ret.add(Arrays.asList(split));
        }
        return ret;
        //return Arrays.asList(parameters).stream().map(parameter->Arrays.asList(((String)parameter).split(","))).collect(Collectors.toList());
    }

    List<List<Object>> listOfEnumSourceObjectsFrom(Method testDataMethod){
        EnumSource annotation = testDataMethod.getAnnotation(EnumSource.class);
        Class<? extends Enum<?>> enumValue = annotation.value();
        if(annotation.value() != null) {
            Enum<?>[] enumConstants = enumValue.getEnumConstants();
            EnumSource.Mode mode = annotation.mode();
            String[] names = annotation.names();
            if(ArrayUtils.isNotEmpty(names)) {
                Set<String> namesSet = new HashSet(Arrays.asList(names));
                Set<String> selectedNamesSet = new HashSet(Arrays.asList(enumConstants).stream().map(Enum::toString).collect(Collectors.toList()));
                switch (mode) {
                    case INCLUDE:
                        selectedNamesSet = namesSet;
                        break;
                    case EXCLUDE:
                        selectedNamesSet.removeAll(namesSet);
                        break;
                    default:
                        break;
                }
                return listOfObjectsFrom(selectedNamesSet.toArray());
            }
            return listOfObjectsFrom(enumConstants);
        }
        return null;
    }

    private List<List<Object>> listOfObjectsFrom(Object[] parameters) {
        return Arrays.asList(parameters).stream().map(parameter->Arrays.asList(parameter)).collect(Collectors.toList());
    }


    private DataTable createParametersTableFrom(String columnNamesString, List<List<Object>> parametersList) {
        int numberOfColumns = parametersList.isEmpty() ? 0 : parametersList.get(0).size();
        List<String> columnNames = split(columnNamesString, numberOfColumns);
        return DataTable.withHeaders(columnNames)
                .andRows(parametersList)
                .build();
    }

    private List<String> split(String columnNamesString, int numberOfColumns) {
        if (StringUtils.isEmpty(columnNamesString)) {
            return numberedColumnHeadings(numberOfColumns);
        }
        return Splitter.on(",").trimResults().omitEmptyStrings().splitToList(columnNamesString);
    }

    private List<String> numberedColumnHeadings(int numberOfColumns) {
        List<String> columnNames = new ArrayList<>();
        for (int i = 0; i < numberOfColumns; i++) {
            columnNames.add("Parameter " + (i + 1));
        }
        return columnNames;
    }



    private boolean findParameterizedTests(Method method) {
        return method.getAnnotation(ParameterizedTest.class) != null &&
                (isAValueSourceAnnotatedMethod(method)
                        || isACsvFileSourceAnnotatedMethod(method)
                        || isACsvSourceAnnotatedMethod(method)
                        || isAEnumSourceAnnotatedMethod(method));
    }

    private boolean isAValueSourceAnnotatedMethod(Method method){
        return method.getAnnotation(ValueSource.class) != null;
    }

    private boolean isAEnumSourceAnnotatedMethod(Method method){
        return method.getAnnotation(EnumSource.class) != null;
    }

    private boolean isACsvSourceAnnotatedMethod(Method method){
        return method.getAnnotation(CsvSource.class) != null;
    }

    private boolean isACsvFileSourceAnnotatedMethod(Method method){
        return method.getAnnotation(CsvFileSource.class) != null;
    }
}