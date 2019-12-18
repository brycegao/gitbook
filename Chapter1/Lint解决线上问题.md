#Android-Lint解决线上问题


> 摘要：贝壳找房app崩溃率一直在千分之三左右， 解决一个crash后又会出现新的crash， 我们的目标是每解决一个crash后，就不再发生同类型的crash。 通过调研发现添加Lint自定义规则可以解决一部分问题。


###一、背景
  在发贝壳2.1.1版本第一次灰度时遇到了一个崩溃（崩溃在租房），第二次灰度版本时发生了相同原因的崩溃（崩在了二手）。 当时的做法是发现一处解决一处， 但没发现的隐藏问题是个定时炸弹。 
  
日志：
java.lang.RuntimeException: Parcelable encountered IOException writing serializable object (name = com.homelink.customer.host.manage.model.response.HostCommentBean) at android.os.Parcel.writeSerializable(Parcel.java:1823) at android.os.Parcel.writeValue(Parcel.java:1771) at android.os.Parcel.writeArrayMapInternal(Parcel.java:838)

###二、问题原因
   这个crash的原因是当前类实现了Serializable接口，但成员数据类型未实现Serializable接口， 导致Activity/Fragment在用Intent传值时出现序列化错误，最终崩在了1823行。
   
```
 try {
1817            ObjectOutputStream oos = new ObjectOutputStream(baos);
1818            oos.writeObject(s);
1819            oos.close();
1820
1821            writeByteArray(baos.toByteArray());
1822        } catch (IOException ioe) {
1823            throw new RuntimeException("Parcelable encountered " +
1824                "IOException writing serializable object (name = " + name +
1825                ")", ioe);
1826        }
```

###三、解决思路
  现有项目代码存在序列化崩溃的潜在风险，如何使用技术手段找出来呢？ 以后如何不再犯相同错误？ 现有的Lint、FindBugs、CheckStyle是备选方案， 最终因为Lint可以实时检查并智能提醒而选择使用Lint实现，即用工具实时提醒开发人员潜在风险。
   
 <center><img src="https://raw.githubusercontent.com/brycegao/open-resource/master/lintrules/result.png" width="400" hegiht="300" align=center /></center>
 <center>期望效果</center> </br>
 
  <center><img src="https://raw.githubusercontent.com/brycegao/open-resource/master/lintrules/result1.png" width="400" hegiht="300" align=center /></center>
 <center>二手插件扫描结果</center> </br>
   使用lint自定义插件扫描二手插件找到所有Serializable崩溃的风险点，从而从根本上解决Serializable序列化崩溃问题。
   
###四、自定义Lint规则
  感觉跟写Java业务代码很像， 只需要了解API功能就可以快速上手。 自定义Lint有个总入口IssueRegistry类，在List里返回需要检查的自定义规则即可。

```
public class SerializableDetector extends Detector implements Detector.UastScanner {

  private static final String CLASS_SERIALIZABLE = "java.io.Serializable";

  private String[] basicTypes = {"byte", "short", "int", "long", "float", "double",
      "char", "boolean", "byte[]", "short[]", "int[]", "long[]", "float[]", "double[]",
      "char[]", "boolean[]","java.lang.String", "java.lang.Double",
      "java.lang.Boolean", "java.lang.Long", "java.lang.Short",
      "java.lang.Integer", "java.lang.Char", "java.lang.Boolean","java.lang.String[]",
      "java.lang.Double[]", "java.lang.Boolean[]", "java.lang.Long[]", "java.lang.Short[]",
      "java.lang.Integer[]", "java.lang.Char[]", "java.lang.Boolean[]"};

  private static HashSet<String> hashSet = new HashSet<>();

  public static final Issue ISSUE = Issue.create(
      "ClassSerializable",
      "Bean类成员需要实现Serializable接口",
      "Bean类成员需要实现Serializable接口",
      Category.SECURITY, 5, Severity.ERROR,
      new Implementation(SerializableDetector.class, Scope.JAVA_FILE_SCOPE));

  @Nullable
  @Override
  public List<String> applicableSuperClasses() {
    //父类是"java.io.Serializable"
    return Collections.singletonList(CLASS_SERIALIZABLE);
  }

  @Override
  public void visitClass(JavaContext context, UClass declaration) {
    if (declaration instanceof UAnonymousClass) {
      return;
    }
    sortClass(context, declaration);
  }

  //递归直到基本数据类型
  private void sortClass(JavaContext context, UClass declaration) {
    if (hashSet.contains(declaration.getPsi().getQualifiedName())) {
      //参考动态规划的备忘录方式，计算出结果的类不再计算第二遍
      //System.out.println(declaration.getPsi().getQualifiedName() + "已经被过滤");
      return;
    }

    UastParser parser = context.getClient().getUastParser(context.getProject());
    boolean isSerialized = false;
    for (PsiClassType psiClassType : declaration.getImplementsListTypes()) {
      if (CLASS_SERIALIZABLE.equals(psiClassType.getCanonicalText())) {
        //实现了序列化
        isSerialized = true;
        break;
      }
    }
    //System.out.println("++++++" + declaration.getPsi().getQualifiedName());
    for (String type : basicTypes) {
      if (type.equalsIgnoreCase(declaration.getPsi().getQualifiedName())) {
        //基本数据类似不需要实现Serializable，继续判断其它成员变量
        return;
      }
    }

    if (!isSerialized) {
      if (!hashSet.contains(declaration.getPsi().getQualifiedName())) {
        context.report(ISSUE,
            declaration.getNameIdentifier(),
            context.getLocation(declaration.getNameIdentifier()),
            String.format("成员变量 `%1$s` 需要实现Serializable接口",
                declaration.getPsi().getQualifiedName()));
        hashSet.add(declaration.getPsi().getQualifiedName());
        System.out.println("size" + hashSet.size() + "/" + declaration.getPsi().getQualifiedName() + "没实现序列化");
      }
      return;
    }

    //检查内部类
    for (UClass uClass : declaration.getInnerClasses()) {
      //递归判断内部类, 查看成员参数是否实现了序列化方法
      sortClass(context, uClass);
    }

    //检查成员变量
    for (UField uField : declaration.getFields()) {
      boolean isBasic = false;
      for (String type : basicTypes) {
        if (type.equalsIgnoreCase(uField.getType().getCanonicalText())) {
          //基本数据类似不需要实现Serializable，继续判断其它成员变量
          isBasic = true;
          break;
        }
      }

      if (isBasic) {
        //如果是基本数据类型继续循环
        continue;
      }

      if (uField.getType().getCanonicalText()
          .matches("^[A-Za-z0-9.]*[List|Set|Map]<[A-Za-z0-9.]*>$")) {
        //使用了泛型则判断泛型是否实现了Serializable
        String genericType = uField.getType().getCanonicalText().substring(
            uField.getType().getCanonicalText().indexOf("<") + 1,
            uField.getType().getCanonicalText().indexOf(">"));

        PsiClass cls = parser.getEvaluator().findClass(genericType);
        UClass uGeneric = context.getUastContext().getClass(cls);
        sortClass(context, uGeneric);
      } else {
        PsiClass psiClass = parser.getEvaluator()
            .findClass(uField.getType().getCanonicalText());
        sortClass(context, context.getUastContext().getClass(psiClass));
      }
    }
  }
}```

###五、集成方式
   考虑到lint规则会更新， 使用maven库管理比较方便，所以采用LinkedIn方式集成到项目里。
   
   在build.gradle里添加debugImplementation 'com.lianjia.lintrules:lib_lintrules:1.0.0-SNAPSHOT@aar'， 
然后gradle sync一下就可以执行自定义lint规则， 也可以执行./gradlew lint输出报告。 
  目前支持lintrules库支持扫描Serializable未序列化类和布局xml引用了未声明类， 后续会根据业务开发遇到的问题新增规则。 
   用技术手段解决线上共性问题是写自定义lint规则的理由， 目的是相同错误不要犯第二次，逐渐降低崩溃率。
   
 