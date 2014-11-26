package com.xcqwan.refreshlistview.app;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/**
 * Created by zombie on 14-11-26.
 */
public class WindRefreshListView extends ListView implements AbsListView.OnScrollListener {
    //正常状态
    private final static int REFRESH_STATE_NONE = 0;
    //进入下拉刷新状态
    private final static int REFRESH_STATE_ENTER = 1;
    //进入松手刷新状态
    private final static int REFRESH_STATE_OVER = 2;
    //松手后反弹后加载状态
    private final static int REFRESH_STATE_EXIT = 3;

    //反弹中
    private final static int MESSAGE_BACKING = 0;
    //反弹结束
    private final static int MESSAGE_BACED = 1;
    //没有弹到顶, 返回
    private final static int MESSAGE_RETURN = 2;
    //加载数据结束
    private final static int MESSAGE_DONE = 3;

    private LinearLayout mHeaderLinearLayout;
    private TextView mHeaderTextView;
    private WindRefreshListener mListener;

    private int mHeaderHeight;
    private int mCurrentScrollState;
    private int mRefreshState = 0;

    private float mDownY;
    private float mMoveY;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_BACKING:
                    //回弹过程
                    mHeaderLinearLayout.setPadding(mHeaderLinearLayout.getPaddingLeft(),
                            (int) (mHeaderLinearLayout.getPaddingTop() * 0.75f),
                            mHeaderLinearLayout.getPaddingRight(),
                            mHeaderLinearLayout.getPaddingBottom());
                    break;
                case MESSAGE_BACED:
                    //显示正在刷新
                    mHeaderTextView.setText("正在刷新...");
                    mRefreshState = REFRESH_STATE_EXIT;
                    setSelection(0);
                    new Thread() {
                        @Override
                        public void run() {
                            if (mListener != null) {
                                mListener.windRefresh();
                            }
                            Message msg = mHandler.obtainMessage();
                            msg.what = MESSAGE_DONE;
                            mHandler.sendMessage(msg);
                        }
                    }.start();
                    break;
                case MESSAGE_RETURN:
                    //未达到刷新界限, 返回
                case MESSAGE_DONE:
                    //刷新结束后, 恢复原始默认状态
                    mHeaderTextView.setText("拉拉拉拉德玛西亚");
                    mHeaderLinearLayout.setPadding(mHeaderLinearLayout.getPaddingLeft(),
                            0,
                            mHeaderLinearLayout.getPaddingRight(),
                            mHeaderLinearLayout.getPaddingBottom());
                    mRefreshState = REFRESH_STATE_NONE;
                    setSelection(1);
                    break;
                default:
                    break;
            }
        }
    };

    public WindRefreshListView(Context context) {
        super(context);
        init(context);
    }

    public WindRefreshListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mHeaderLinearLayout = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.header_refresh, null);
        addHeaderView(mHeaderLinearLayout);

        mHeaderTextView = (TextView) mHeaderLinearLayout.findViewById(R.id.tv_refresh);

        setSelection(1);
        setOnScrollListener(this);

        measureView(mHeaderLinearLayout);
        mHeaderHeight = mHeaderLinearLayout.getMeasuredHeight();
    }

    public void setWindRefreshListener(WindRefreshListener listener) {
        mListener = listener;
    }

    /**
     * 因为是在构造函数里测量高度，所以应该先measure一下
     * @param child
     */
    private void measureView(View child) {
        ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        int childWidthSpec = ViewGroup.getChildMeasureSpec(0, 0, layoutParams.width);
        int lpHeight = layoutParams.height;
        int childHeightSpec;
        if (lpHeight > 0) {
            childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
        } else {
            childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    @Override
    public void onScrollStateChanged(AbsListView absListView, int scrollState) {
        mCurrentScrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView absListView, int firstVisible, int visibleCount, int totalCount) {
        if (mCurrentScrollState == SCROLL_STATE_TOUCH_SCROLL) {
            if (firstVisible == 0) {
                //从正常状态进入下拉刷新状态
                if (mHeaderLinearLayout.getBottom() >= 0 && mHeaderLinearLayout.getBottom() < mHeaderHeight && mRefreshState == REFRESH_STATE_NONE) {
                    mRefreshState = REFRESH_STATE_ENTER;
                }
                //下拉高度超过Header的高度时, 进入松手刷新状态
                if (mHeaderLinearLayout.getBottom() >= mHeaderHeight && (mRefreshState == REFRESH_STATE_ENTER || mRefreshState == REFRESH_STATE_NONE)) {
                    mRefreshState = REFRESH_STATE_OVER;
                    mDownY = mMoveY;
                    mHeaderTextView.setText("该放手时就放手");
                }
            } else {
                //下拉后又推上去, 则取消刷新
                if (mRefreshState == REFRESH_STATE_ENTER) {
                    mRefreshState = REFRESH_STATE_NONE;
                    mHeaderTextView.setText("拉拉拉拉德玛西亚");
                }
            }
        } else if (mCurrentScrollState == SCROLL_STATE_FLING && firstVisible == 0) {
            //飞滑状态，不能显示出header，也不能影响正常的飞滑
            //只在正常情况下才纠正位置
            if (mRefreshState == REFRESH_STATE_NONE) {
                setSelection(1);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //记下按下位置
                mDownY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                //移动时手指的位置
                mMoveY = ev.getY();
                if (mRefreshState == REFRESH_STATE_OVER) {
                    //进入松手刷新状态后, 下拉距离打折
                    mHeaderLinearLayout.setPadding(mHeaderLinearLayout.getPaddingLeft(),
                            (int) ((mMoveY - mDownY) / 3),
                            mHeaderLinearLayout.getPaddingRight(),
                            mHeaderLinearLayout.getPaddingBottom());
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mRefreshState == REFRESH_STATE_OVER || mRefreshState == REFRESH_STATE_ENTER) {
                    new Thread() {
                        @Override
                        public void run() {
                            Message msg;
                            while (mHeaderLinearLayout.getPaddingTop() > 1) {
                                msg = mHandler.obtainMessage();
                                msg.what = MESSAGE_BACKING;
                                mHandler.sendMessage(msg);
                                try {
                                    sleep(5);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            msg = mHandler.obtainMessage();
                            if (mRefreshState == REFRESH_STATE_OVER) {
                                msg.what = MESSAGE_BACED;
                            } else {
                                msg.what = MESSAGE_RETURN;
                            }
                            mHandler.sendMessage(msg);
                        }
                    }.start();
                }
                break;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
        setSelection(1);
    }

    public interface WindRefreshListener {
        void windRefresh();
    }
}
