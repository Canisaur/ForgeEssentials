package com.forgeessentials.jscripting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.IOUtils;

import com.forgeessentials.jscripting.wrapper.JsWindowStatic;
import com.forgeessentials.jscripting.wrapper.item.JsItemStatic;
import com.forgeessentials.jscripting.wrapper.server.JsPermissionsStatic;
import com.forgeessentials.jscripting.wrapper.server.JsServerStatic;
import com.forgeessentials.jscripting.wrapper.world.JsBlockStatic;
import com.forgeessentials.jscripting.wrapper.world.JsWorldStatic;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;
import com.sun.tools.javadoc.Main;

public class TsdGenerator extends Doclet
{

    public static TsdGenerator generator = new TsdGenerator();

    public static final String NAMESPACE = "mc";
    public static final String PACKAGE = "com.forgeessentials.jscripting.wrapper";

    private File outFile = new File("jscripting/mc.d.ts");

    private PrintStream writer;

    private int indention = 0;

    private String packageName;

    private String fullPackageName;

    private Set<String> declaredTypes = new HashSet<>();

    private Map<String, String> classNameMap = new HashMap<>();

    public boolean startImpl(RootDoc root)
    {
        declaredTypes.add("int");
        declaredTypes.add("long");
        declaredTypes.add("float");
        declaredTypes.add("double");
        declaredTypes.add("string");
        declaredTypes.add("boolean");
        declaredTypes.add("any");
        declaredTypes.add("number");
        declaredTypes.add("void");
        declaredTypes.add("JavaList");
        declaredTypes.add("JavaObject");

        classNameMap.put("void", "void");
        classNameMap.put("java.lang.Object", "any");
        classNameMap.put("java.lang.String", "string");
        classNameMap.put("java.lang.Boolean", "boolean");
        classNameMap.put("com.forgeessentials.util.MappedList", "JavaList");

        List<String> externalClasses = new ArrayList<>();
        externalClasses.add(UUID.class.getName());

        List<String> staticClasses = new ArrayList<>();
        staticClasses.add(JsWindowStatic.class.getName());
        staticClasses.add(JsServerStatic.class.getName());
        staticClasses.add(JsWorldStatic.class.getName());
        staticClasses.add(JsBlockStatic.class.getName());
        staticClasses.add(JsItemStatic.class.getName());
        staticClasses.add(JsPermissionsStatic.class.getName());

        try
        {
            try (PrintStream w = new PrintStream(new FileOutputStream(outFile)))
            {
                writer = w;
                indention = 0;
                write(IOUtils.toString(ScriptInstance.class.getResource("tsd_header.d.ts")));
                indention = 1;

                List<PackageDoc> packages = Arrays.asList(root.specifiedPackages());
                packages.sort((a, b) -> a.name().compareTo(b.name()));
                Collections.swap(packages, 0, packages.size() - 1);

                packageName = "";
                fullPackageName = NAMESPACE;
                for (String externalClass : externalClasses)
                    preprocessClass(root.classNamed(externalClass));

                for (PackageDoc packageDoc : packages)
                {
                    packageName = packageDoc.name().substring(Math.min(packageDoc.name().length(), PACKAGE.length() + 1));
                    fullPackageName = NAMESPACE + (packageName.length() == 0 ? "" : "." + packageName);
                    for (ClassDoc classDoc : packageDoc.allClasses())
                        preprocessClass(classDoc);
                }

                for (PackageDoc packageDoc : packages)
                {
                    packageName = packageDoc.name().substring(Math.min(packageDoc.name().length(), PACKAGE.length() + 1));
                    fullPackageName = NAMESPACE + (packageName.length() == 0 ? "" : "." + packageName);
                    if (packageName.length() > 0)
                    {
                        writeComment(packageDoc);
                        writeLn("namespace " + packageName + " {");
                        indention++;
                        writeLn("");
                    }

                    List<ClassDoc> classes = Arrays.asList(packageDoc.allClasses());
                    classes.sort((a, b) -> a.name().compareTo(b.name()));

                    for (ClassDoc classDoc : classes)
                        generateClass(classDoc);

                    if (packageName.length() > 0)
                    {
                        indention--;
                        writeLn("}");
                        writeLn("");
                    }
                }
                packageName = "";
                fullPackageName = NAMESPACE;

                for (String externalClass : externalClasses)
                    generateClass(root.classNamed(externalClass));

                // for (String type : knownTypes)
                List<String> undefinedTypes = new ArrayList<>(classNameMap.values());
                undefinedTypes.sort((a, b) -> a.compareTo(b));
                for (String type : undefinedTypes)
                {
                    if (declaredTypes.contains(type))
                        continue;

                    System.err.println("Warning: Type " + type + " not defined!");

                    type = type.substring(NAMESPACE.length() + 1);
                    if (type.contains("."))
                    {
                        writeLn("namespace ");
                        write(stripPackageName(type));
                        write(" { ");
                        write("interface ");
                        write(stripClassName(type));
                        write(" { } }");
                    }
                    else
                    {
                        writeLn("interface ");
                        write(type);
                        write(" { }");
                    }
                    writeLn("");
                }

                indention--;
                writeLn("}");
                writeLn("");
                for (String className : staticClasses)
                {
                    String mappedName = classNameMap.get(className);
                    String varName = mappedName.substring(mappedName.lastIndexOf('.') + 1, mappedName.length() - "Static".length());
                    writeLn("declare var ");
                    write(varName.equals("Window") ? "window" : varName);
                    write(": ");
                    write(mappedName);
                    write(";");
                }
                writeLn("");

                // Generate window public defs
                ClassDoc window = root.classNamed(JsWindowStatic.class.getName());
                for (FieldDoc fieldDoc : window.fields())
                    generateField(fieldDoc);
                for (MethodDoc methodDoc : window.methods())
                    generateMethod(methodDoc, false);
                writeLn("");
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        return false;
    }

    private void preprocessClass(ClassDoc classDoc)
    {
        if (ignoreClass(classDoc))
            return;
        String typeName = getFirstTagText(classDoc, "tsd.type");
        if (typeName == null)
            typeName = mapClassName(classDoc);
        else
        {
            typeName = fullPackageName + "." + typeName;
            classNameMap.put(classDoc.qualifiedName(), typeName);
        }
    }

    private void generateClass(ClassDoc classDoc)
    {
        if (ignoreClass(classDoc))
            return;
        String typeName = classNameMap.get(classDoc.qualifiedName());

        boolean isClass = !typeName.endsWith("Static");

        writeComment(classDoc);

        // Write interface header
        writeLn(isClass ? "class " : "interface ");
        write(stripClassName(typeName));
        if (classDoc.superclass() != null && !classDoc.superclass().qualifiedName().equals("java.lang.Object"))
        {
            write(" extends ");
            write(mapClassName(classDoc.superclassType()));
            if (classDoc.superclassType() instanceof ParameterizedType && classDoc.superclass().name().equals("MappedList"))
            {
                write("<");
                write(mapClassName(classDoc.superclassType().asParameterizedType().typeArguments()[1]));
                write(">");
            }
        }
        write(" {");

        indention++;

        for (FieldDoc fieldDoc : classDoc.fields())
            generateField(fieldDoc);

        if (isClass)
        {
            for (MethodDoc methodDoc : classDoc.methods())
                generateMethod(methodDoc, true);

            for (ConstructorDoc constructorDoc : classDoc.constructors())
                generateConstructor(constructorDoc);
        }

        for (MethodDoc methodDoc : classDoc.methods())
            generateMethod(methodDoc, false);

        indention--;
        writeLn("}");
        writeLn("");
        declaredTypes.add(typeName);
    }

    private void generateConstructor(ConstructorDoc constructorDoc)
    {
        if (!constructorDoc.isPublic() || ignoreDoc(constructorDoc))
            return;
        if (constructorDoc.parameters().length > 0 && constructorDoc.parameters()[0].name().equals("that"))
            return;

        writeComment(constructorDoc);

        if (indention == 0)
            writeLn("declare function ");
        else
            writeLn("constructor");

        Tag[] defTags = constructorDoc.tags("tsd.def");
        if (defTags.length > 0)
        {
            for (Tag tag : defTags)
            {
                write(tag.text());
            }
            return;
        }

        write("(");
        Parameter[] parameters = constructorDoc.parameters();
        for (int i = 0; i < parameters.length; i++)
        {
            if (i > 0)
                write(", ");
            Parameter parameter = parameters[i];
            if (constructorDoc.isVarArgs() && i == parameters.length - 1)
                write("...");
            write(parameter.name());
            write(": ");
            write(mapClassName(parameter.type()));
            write(parameter.type().dimension());
        }
        write(");");
    }

    private void generateField(FieldDoc fieldDoc)
    {
        if (!fieldDoc.isPublic() || fieldDoc.isStatic() || ignoreDoc(fieldDoc))
            return;

        writeComment(fieldDoc);

        if (indention == 0)
            writeLn("declare var ");
        else
            writeLn("");

        Tag[] defTags = fieldDoc.tags("tsd.def");
        if (defTags.length > 0)
        {
            for (Tag tag : defTags)
            {
                write(tag.text());
            }
            return;
        }

        write(fieldDoc.name());

        if (fieldDoc.tags("tsd.optional").length > 0)
            write("?");
        write(": ");

        writeType(fieldDoc, fieldDoc.type());
        write(";");
    }

    private void generateMethod(MethodDoc methodDoc, boolean staticOnly)
    {
        if (!methodDoc.isPublic() || ignoreDoc(methodDoc))
            return;
        if (methodDoc.isStatic() != staticOnly)
            return;

        writeComment(methodDoc);

        writeLn(indention == 0 ? "declare function " : (staticOnly ? "static " : ""));

        Tag[] defTags = methodDoc.tags("tsd.def");
        if (defTags.length > 0)
        {
            for (Tag tag : defTags)
            {
                write(tag.text());
            }
            return;
        }

        write(methodDoc.name());

        write("(");
        Parameter[] parameters = methodDoc.parameters();
        for (int i = 0; i < parameters.length; i++)
        {
            if (i > 0)
                write(", ");
            Parameter parameter = parameters[i];
            if (methodDoc.isVarArgs() && i == parameters.length - 1)
                write("...");
            write(parameter.name());
            write(": ");
            write(mapClassName(parameter.type()));
            write(parameter.type().dimension());
        }
        write("): ");
        writeType(methodDoc, methodDoc.returnType());
        write(";");
    }

    private void writeType(Doc doc, Type type)
    {
        String typeOverride = getFirstTagText(doc, "tsd.type");
        write(typeOverride != null ? typeOverride : mapClassName(type));
        if (type instanceof ParameterizedType)
        {
            if (type.simpleTypeName().equals("MappedList"))
            {
                write("<");
                write(mapClassName(type.asParameterizedType().typeArguments()[1]));
                write(">");
            }
        }
        else
        {
            write(type.dimension());
        }
    }

    private void writeComment(Doc fieldDoc)
    {
        String deprecation = getFirstTagText(fieldDoc, "deprecated");
        if (fieldDoc.commentText().length() > 0 || deprecation != null)
        {
            writeLn("/**");
            String comment = fieldDoc.commentText()
                    .replace("\r", "")
                    .replace("<br>", "")
                    .replace("<b>", "")
                    .replace("</b>", "");
            if (comment.length() > 0)
                for (String line : comment.split("\n"))
                    writeLn(" * " + line.trim());
            if (deprecation != null)
                writeLn(" * @deprecated " + deprecation);
            writeLn(" */");
        }
    }

    private boolean ignoreDoc(Doc doc)
    {
        return doc.name().startsWith("_") || doc.tags("tsd.ignore").length > 0;
    }

    private boolean ignoreClass(ClassDoc classDoc)
    {
        return !classDoc.isPublic() || ignoreDoc(classDoc); // || !classDoc.name().startsWith("Js");
    }

    private String getFirstTagText(Doc doc, String name)
    {
        Tag[] tags = doc.tags(name);
        return tags.length > 0 ? tags[0].text() : null;
    }

    private String mapClassName(Type type)
    {
        // Directly return simple types like void and int
        if (!type.qualifiedTypeName().contains("."))
            return type.qualifiedTypeName();

        String mappedName = classNameMap.get(type.qualifiedTypeName());
        if (mappedName == null)
        {
            mappedName = stripClassName(type.typeName());
            if (mappedName.startsWith("Js"))
                mappedName = mappedName.substring(2);

            mappedName = fullPackageName + "." + mappedName;
            classNameMap.put(type.qualifiedTypeName(), mappedName);
        }

        if (indention > 0 && mappedName.startsWith(fullPackageName))
            return mappedName.substring(fullPackageName.length() + 1);
        return mappedName;
    }

    private String stripPackageName(String name)
    {
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(0, idx);
    }

    private String stripClassName(String name)
    {
        int idx = name.lastIndexOf('.');
        return idx < 0 ? name : name.substring(idx + 1);
    }

    private void print(String text)
    {
        // System.out.print(text);
        writer.print(text);
    }

    private void writeLn(String text)
    {
        print("\r\n");
        for (int i = 0; i < indention; i++)
            print("\t");
        print(text);
    }

    private void write(String text)
    {
        print(text);
    }

    /* ************************************************************ */
    /* Doclet functions */

    public static boolean start(RootDoc root)
    {
        return generator.startImpl(root);
    }

    public static int optionLength(String arg)
    {
        switch (arg)
        {
        case "-out":
            return 2;
        }
        return 0;
    }

    public static boolean validOptions(String[][] argGroups, DocErrorReporter arg0)
    {
        for (String[] argGroup : argGroups)
        {
            switch (argGroup[0])
            {
            case "-out":
                System.out.println("Set output file to " + argGroup[1]);
                generator.outFile = new File(argGroup[1]);
                break;
            }
        }
        return true;
    }

    public static LanguageVersion languageVersion()
    {
        return LanguageVersion.JAVA_1_5;
    }

    public static void main(String[] args)
    {
        Main.main(new String[] {
                "-sourcepath", "src/main/java",
                "-doclet", TsdGenerator.class.getName(),
                "-out", "jscripting/mc.d.ts",
                "-public",
                "-subpackages", PACKAGE
        });
    }

}
