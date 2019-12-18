#解析安卓bundle值

## 开发背景

安卓Activity、Fragment在onCreate函数里会解析bundle里的值并填充到类成员变量中， 如果可能使用多个key值的话导致代码比较杂乱。

## 实现效果
解析bundle对象时不再需要bundle.get***方法， 而是在类成员变量上添加注解即可。 是不是很方便？
解析bundle：

```
public class SecondActivity extends Activity {
  @BundleParam(value = ConstantUtils.KEY_NAME)
  private String mName;

  @BundleParam(value = ConstantUtils.KEY_ADDRESS, alternate = {ConstantUtils.KEY_ADDRESS2},
         defaultVal = "北京市海淀区***路***号")
  private String mAddr;

  @BundleParam(value = ConstantUtils.KEY_AGE, defaultVal = "0")
  private int mAge;

  @BundleParam(value = ConstantUtils.KEY_OBJ_SERIAL, isSerialable = true)
  private SerializeBean mSerialBean;

  @BundleParam(value = ConstantUtils.KEY_OBJ_PARCEL, isParcelable = true)
  private ParcelBean mParcelBean;

  @BundleParam(value = ConstantUtils.KEY_ARRAY_INT)
  private int[] mIntArray;

  @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_second_main);

    long beginTime = System.currentTimeMillis();
    ParseBundle.initParams(this);
    long calcTime = System.currentTimeMillis() - beginTime;
    Log.d("brycegao", "解析用时：" + calcTime);

    Log.d("brycegao", "data:" + mName + ", " + mAddr + ", " + mAge
        + ", bean:" + mSerialBean
        + ", parcel:" + mParcelBean);

  }
}

```
发送数据

```
 SerializeBean bean = new SerializeBean();
                bean.mAddr = "北京市海淀";
                bean.mAge = 11;
                bean.mName = "brycegao";

                ParcelBean parcelBean = new ParcelBean();
                parcelBean.mAddr = "aaaaaaaaa";
                parcelBean.mAge = 22;
                parcelBean.mName = "bbb";

                int[] array = new int[]{1,2,3,4};

                Intent intent = new Intent(MainActivity.this, SecondActivity.class);
                intent.putExtra(ConstantUtils.KEY_NAME, "张三");
                intent.putExtra(ConstantUtils.KEY_AGE, 20);
                intent.putExtra(ConstantUtils.KEY_OBJ_SERIAL, bean);
                intent.putExtra(ConstantUtils.KEY_OBJ_PARCEL, parcelBean);
                intent.putExtra(ConstantUtils.KEY_ARRAY_INT, array);
                startActivity(intent);
            }
        });
```

## 说明
目前支持Java基本数据类型和String、Parcelable、Serializable类型的解析。

代码详见：
[https://github.com/brycegao/parsebundle](https://github.com/brycegao/parsebundle)