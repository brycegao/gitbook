#自定义Android gradle插件打印函数执行周期和参数


> 摘要：在性能调优时要分析函数执行周期，安卓提供了Android Profiler和MethodTracing等方式， 但缺点是打印的日志太多且缺少参数值。

###一、背景
在日常开发工作中，当遇到执行慢/卡等问题时，一般会使用Android Profiler或MethodTracking抓取日志， 但这两种方式的缺点是输出的日志太多且缺少参数值； 所以我们会在怀疑有问题的函数里添加日志语句， 待解决问题后再删除这些日志。 例如：在Activity的onCreate函数体前后添加日志语句输出该函数执行时间和参数值。

```
  protected void onCreate(Bundle savedInstanceState) {
    long start = System.currentTimeMillis();
    
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    long end = System.currentTimeMillis();
    Log.d("brycegao", "MainActivity耗时：" + (end-start)
            + " , 参数saveInstanceState:" + savedInstanceState);

  }
```
  每次调试时可能有新的怀疑点，那么就要像上面示例代码那样在所有函数里添加类似代码， 这是个繁琐的体力活。
  
###二、参考方案 
  在编译过程中注入代码， 不再手写这些没营养的日志代码。 
   
目前成熟的方案是大神JakeWharton写的hugo库， 它是基于AspectJ实现的， 属于面前切面编程（AOP)；简单的讲就是解决一类问题， 属于面向函数编程思想。 查看源码总共就几个文件，主要是在build.gradle里配置AspectJ。 hugo基本原理：在编译期间查找@DebugLog注解并注入字节码。

###三、解决方案
  不使用AspectJ，自定义gradle插件使用Transform和Javassist实现相同的效果，优点是编译输出物不用集成aspectj库，从而减少安装包大小。
 
用法：</br>
在工程build.gradle添加 classpath 'com.brycegao.timeplugin:timeplugin:1.0.5'和        maven { url "https://dl.bintray.com/brycegmail/maven" }
 </br>
在app模块build.gradle api 'com.brycegao.tpannotation:tpannotation:1.0.2' 和apply plugin: 'timeplugin'  
 
原理：

```
@DebugLogger
public class MainActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    showMsg(1, "this is test");

    findViewById(R.id.btn_next).setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View view) {
        Intent intent = new Intent(MainActivity.this, SecondActivity.class);
        startActivity(intent);
      }
    });
  }

  private void showMsg(int i, String msg) {
    try {
      Thread.sleep(100); //仅仅为了测试
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  @Override public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
  }
}
```
生成的字节码：

```
@DebugLogger
public class MainActivity extends Activity {
  public MainActivity() {
  }

  protected void onCreate(Bundle savedInstanceState) {
    long startTime = System.currentTimeMillis();
    String classPath = "com.byrcegao.tpdemo.MainActivity";
    String methodName = "onCreate";
    super.onCreate(savedInstanceState);
    this.setContentView(2130968576);
    this.showMsg(1, "this is test");
    this.findViewById(2130903040).setOnClickListener(new OnClickListener() {
      public void onClick(View view) {
        Intent intent = new Intent(MainActivity.this, SecondActivity.class);
        MainActivity.this.startActivity(intent);
      }
    });
    Object var9 = null;
    long endTime = System.currentTimeMillis();
    Log.d("MethodTime", classPath + ":" + methodName + "耗时：" + (endTime - startTime) + "毫秒");
    Object var11 = null;
    Log.d("MethodTime", classPath + ":" + methodName + "参数：" + "savedInstanceState" + ":" + savedInstanceState);
  }

  private void showMsg(int i, String msg) {
    long startTime = System.currentTimeMillis();
    String classPath = "com.byrcegao.tpdemo.MainActivity";
    String methodName = "showMsg";

    try {
      Thread.sleep(100L);
    } catch (Exception var14) {
      var14.printStackTrace();
    }

    Object var11 = null;
    long endTime = System.currentTimeMillis();
    Log.d("MethodTime", classPath + ":" + methodName + "耗时：" + (endTime - startTime) + "毫秒");
    Object var13 = null;
    Log.d("MethodTime", classPath + ":" + methodName + "参数：" + "i" + ":" + i + "参数：" + "msg" + ":" + msg);
  }

  public void onWindowFocusChanged(boolean hasFocus) {
    long startTime = System.currentTimeMillis();
    String classPath = "com.byrcegao.tpdemo.MainActivity";
    String methodName = "onWindowFocusChanged";
    super.onWindowFocusChanged(hasFocus);
    Object var9 = null;
    long endTime = System.currentTimeMillis();
    Log.d("MethodTime", classPath + ":" + methodName + "耗时：" + (endTime - startTime) + "毫秒");
    Object var11 = null;
    Log.d("MethodTime", classPath + ":" + methodName + "参数：" + "hasFocus" + ":" + hasFocus);
  }
}
```  

###四、实现原理
  Transform是在构建阶段的javac之后，生成dex之前， 即Transform的输入是字节码、输出也是字节码。 多个transfrom时按照build.gradle配置的先后顺序执行。
<center> 
<img src="https://raw.githubusercontent.com/brycegao/open-resource/master/transform/transform.png" width="50%" height="50%" />
</center>
<center>transform原理</center>

集成到贝壳找房app中并在ApplicationInit.java添加类注解DebugLogger后编译， 打开build/intermediates/transforms目录可以看到所有的transform，

<center> 
<img src="https://raw.githubusercontent.com/brycegao/open-resource/master/transform/ng.png" width="50%" height="50%" />
</center>
<center>集成到贝壳找房</center>
点击桌面图标启动贝壳找房app后查看日志。

<center> 
<img src="https://raw.githubusercontent.com/brycegao/open-resource/master/transform/log.png" width="50%" height="50%" />
</center>

###五、扩展
在编译期间还可以注入业务代码，例如登录判断、权限判断等都可以定义个注解，在编译期间注入对应的代码。 优点是统一管理。