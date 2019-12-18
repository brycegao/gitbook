#安卓9启动Activity流程分析和优化建议


> 摘要：分析Android9.0启动Activity流程并给出优化建议；

思考如下问题：

1、Android9和之前版本startActivity的差异？

2、为什么startActivity是异步的？

3、为什么安卓Looper死循环不会导致UI卡死？


Android从9.0版本开始修改了启动Activity部分流程， 跟以往版本的主要区别在于使用Transaction并删除了ActivityThread内部类H中100~109的code。
   
从Android8.0开始使用AIDL替换Binder实现系统进程(SystemServer)和应用进程之间的通讯。
   <center><img src="https://raw.githubusercontent.com/brycegao/open-resource/master/startactivity/启动Activity.jpg" width="700" hegiht="400" align=center /></center>
<center>Android9时序图</center>

Android9.0版本startActivity后会在clientTransaction.addCallback函数传入LaunchActivityItem对象。

```
final boolean realStartActivityLocked(ActivityRecord r, ProcessRecord app,
            boolean andResume, boolean checkConfig) throws RemoteException {
        }   
 ......
clientTransaction = ClientTransaction.obtain(app.thread,
                        r.appToken);
                clientTransaction.addCallback(LaunchActivityItem.obtain(new Intent(r.intent),
                        System.identityHashCode(r), r.info,
                        // TODO: Have this take the merged configuration instead of separate global
                        // and override configs.
                        mergedConfiguration.getGlobalConfiguration(),
                        mergedConfiguration.getOverrideConfiguration(), r.compat,
                        r.launchedFromPackage, task.voiceInteractor, app.repProcState, r.icicle,
                        r.persistentState, results, newIntents, mService.isNextTransitionForward(),
                        profilerInfo));
   ....
}
```

Android9.0以前版本直接调用ActivityThread的scheduleLaunchActivity方法发送LAUNCH_ACTIVITY消息。

```
final boolean realStartActivityLocked(ActivityRecord r, ProcessRecord app,
           boolean andResume, boolean checkConfig) throws RemoteException {

       ......

        app.thread.scheduleLaunchActivity(new Intent(r.intent), r.appToken,
                   System.identityHashCode(r), r.info,
                   // TODO: Have this take the merged configuration instead of separate global and
                   // override configs.
                   mergedConfiguration.getGlobalConfiguration(),
                    mergedConfiguration.getOverrideConfiguration(), r.compat,
                    r.launchedFromPackage, task.voiceInteractor, app.repProcState, r.icicle,
                    r.persistentState, results, newIntents, !andResume,
                    mService.isNextTransitionForward(), profilerInfo);
}
```
**因为都调用了sendMessage， 所以startActivity是异步操作；**

Android9.0以前版本在执行了startActivity后会向主线程MessageQueue中新增一个Message， 如下图所示：
 <center><img src="https://raw.githubusercontent.com/brycegao/open-resource/master/startactivity/android9.0以前.png" width="700" hegiht="400" align=center /></center>
<center>android8.0 startActivity</center>

Android9.0及后续版本AndroidQ新增what等于159即EXECUTE_TRANSACTION的消息， 且obj是ClientTransaction类对象。
 <center><img src="https://raw.githubusercontent.com/brycegao/open-resource/master/startactivity/android9.0以后.png" width="700" hegiht="400" align=center /></center>
<center>android9.0 startActivity</center>


因为Android消息是个队列， 当启动Activity时会向队列尾部新增一条Message， 如果消息队列中有耗时操作的Message会导致启动Activity时机延后
 <center><img src="https://raw.githubusercontent.com/brycegao/open-resource/master/startactivity/移动消息.png" width="700" hegiht="400" align=center /></center>
<center>调整Message位置</center>


安卓Looper会循环消费MessageQueue中的Message（注意：并不是真正的先入先出，原因是异步消息可以插队）， 想让某个Message提前执行可以动态调整它在队列中的位置。
<center><img src="https://raw.githubusercontent.com/brycegao/open-resource/master/startactivity/消息循环.png" width="700" hegiht="400" align=center /></center>
<center>Looper工作原理</center>

由于Linux的epoll机制， 保证了Looper.loop的死循环获取消息时并不会阻塞主线程。 当主线程无消息时进入睡眠状态并阻塞等待nativePollOnce函数， 有消息时唤醒nativePollOnce函数并继续向下执行。
<center><img src="https://raw.githubusercontent.com/brycegao/open-resource/master/startactivity/消息处理方式.png" width="700" hegiht="400" align=center /></center>
<center>Message死循环原理</center>

启动优化建议：

1、如果消息队里中有耗时操作的Message， 建议运行时修改启动Activity对应Message在队列中的位置， 从而达到提前启动Activity的目的。

2、App冷启动或启动Activity时将一部分必须运行在主线程的任务调整到IdleHandler中。

调整启动Activity对应Message时序示例代码：

```

public class MainActivity extends AppCompatActivity {
    private TextView mTvInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvInfo = findViewById(R.id.tv_info);

        findViewById(R.id.btn_ok).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
              //new Handler().post(new Runnable() {
              //  @Override public void run() {
              //    try {
              //      Thread.sleep(200);
              //    } catch (InterruptedException ex) {
              //      ex.printStackTrace();
              //    }
              //  }
              //});
                Log.d("brycegao", "------------------queue111111111");

                Intent intent = new Intent(MainActivity.this, SecondActivity.class);
                startActivity(intent);
                MessageQueue queue = Looper.myQueue();
                changeMsgSequence(queue);
                Log.d("brycegao", "------------------queue22222222");
            }
        });
    }

    private void changeMsgSequence(MessageQueue queue) {
      Class clz = MessageQueue.class;
      try {
        Field field = clz.getDeclaredField("mMessages");
        field.setAccessible(true);
        Object obj = field.get(queue);
        if (obj != null && obj instanceof Message) {
          Message msg = (Message) obj;
          Message newHead; //新的mMessage值
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            newHead = alterMsgOrderBeforeAndroidP(msg);
          } else {
            //Android9及以上版本
            newHead = alterMsgOrderAfterAndroidP(msg);
          }
          field.set(queue, newHead);
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }

    }

    private Message getNextMessage(Message msg) {
        Class clz = Message.class;
        try {
            Field field = clz.getDeclaredField("next");
            field.setAccessible(true);
            Object object = field.get(msg);
            if (object != null && object instanceof Message) {
                return (Message) object;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * 设置当前Message的下一个为next
     * @param cur
     * @param next
     */
    private void setNextMessage(Message cur, Message next) {
        Class clz = Message.class;
        try {
            Field field = clz.getDeclaredField("next");
            field.setAccessible(true);
            field.set(cur, next);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Android9及后续版本Q移动activity启动消息到队列头部
     * @param msg， 主线程MessageQueue的mMessages
     * @return
    */
    private Message alterMsgOrderAfterAndroidP(final Message msg) {
      Message head = msg;
      Message destMsg = msg;
      Message last = msg;

      while (head != null) {
        if (head.what == 159 && head.getTarget() != null
            && head.getTarget().getClass().toString().contains("android.app.ActivityThread$H")
            && isObjLaunchActivityItem(head.obj)) {
          Message nextMsg = getNextMessage(head);
          setNextMessage(last, nextMsg);
          setNextMessage(head, msg);
          return head;
        }
        last = head;
        head = getNextMessage(head);
      }
      return destMsg;
    }

  /**
   * 判断Message的obj参数是否匹配启动Activity
   * @param object, Message的obj参数
   * @return
   */
    private boolean isObjLaunchActivityItem(Object object) {
      if (object == null || !object.getClass().toString()
          .contains("android.app.servertransaction.ClientTransaction")) {
        return false;
      }

      try {
        Class clz = Class.forName("android.app.servertransaction.ClientTransaction");
        Field field = clz.getDeclaredField("mActivityCallbacks");
        field.setAccessible(true);
        Object data = field.get(object);
        if (data != null && data instanceof List) {
          List items = (List)data;
          if (items.size() > 0) {
            Object tranItem = items.get(0);
            if (tranItem.getClass().toString().endsWith("LaunchActivityItem")) {
               return true;
            }
          }
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      return false;
    }

    private Message alterMsgOrderBeforeAndroidP(final Message msg) {
        Message head = msg;
        Message destMsg = msg;   //新的队列头，即启动activity消息
        Message last = msg;

        while (head != null) {
            if (head.what == 100 && head.getTarget() != null
                && head.getTarget().getClass().toString().contains("android.app.ActivityThread$H")) {
                //判断是启动activity的消息
                //{ when=-1m0s908ms what=100 obj=ActivityRecord{672a27c token=android.os.BinderProxy@4392705 {com.brycegao.findview/com.brycegao.findview.SecondActivity}} target=android.app.ActivityThread$H }
                Message nextMsg = getNextMessage(head);
                setNextMessage(last, nextMsg);
                setNextMessage(head, msg);
                return head;
            }

            last = head;
            head = getNextMessage(head);
        }

        return destMsg;
    }
    
```

