package com.infiRay.XthermDemo;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Administrator on 2018/1/29 0029.
 */

public class ThermometrySetting  extends Fragment  {
    private static final String TAG = "ThermometrySetting";
    private UVCCameraHandler mCameraHandler;
    private float Fix=0 , Refltmp=0, Airtmp=0, humi=0, emiss=0;
    private short distance=0;
    private String stFix,stRefltmp,stAirtmp,stHumi,stEmiss,stDistance;
    private ListView mLvThermMember;
    private ImageButton mIbThermBack;
    private ThermSettingAdapter mThermAdapter;
    private ByteUtil mByteUtil=new ByteUtil();
    private sendCommand mSendCommand=new sendCommand();
    private EditText iput;

    private void getTempPara(){
        byte[] tempPara;
        tempPara=mCameraHandler.getTemperaturePara(128);
//        Log.e(TAG, "getByteArrayTemperaturePara:"+tempPara[16]+","+tempPara[17]+","+tempPara[18]+","+tempPara[19]+","+tempPara[20]+","+tempPara[21]);

        Fix=mByteUtil.getFloat(tempPara,0);
        Refltmp=mByteUtil.getFloat(tempPara,4);
        Airtmp=mByteUtil.getFloat(tempPara,8);
        humi=mByteUtil.getFloat(tempPara,12);
        emiss=mByteUtil.getFloat(tempPara,16);
        distance=mByteUtil.getShort(tempPara,20);
        stFix=String.valueOf(Fix);
        stRefltmp=String.valueOf(Refltmp);
        stAirtmp=String.valueOf(Airtmp);
        stHumi=String.valueOf(humi);
        stEmiss=String.valueOf(emiss);
        stDistance=String.valueOf(distance);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //initImageLoader();
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mCameraHandler=UVCCameraHandler.getInstance();
        getTempPara();
        View view = inflater.inflate(R.layout.thermsetting_fragment, container,
                false);
        mLvThermMember = view.findViewById(R.id.thermLvSettingMember);
        mIbThermBack= view.findViewById(R.id.thermSetting_back);
        if(null == mThermAdapter){
            mThermAdapter = new ThermSettingAdapter(getActivity());
        }
        mLvThermMember.setAdapter(mThermAdapter);
        mIbThermBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentManager fragmentManager=getFragmentManager();
                Fragment thermSetFragment=fragmentManager.findFragmentByTag("ThermometrySetting");
                Fragment setFragment=fragmentManager.findFragmentByTag("setting");
                FragmentTransaction fragmentTransaction=fragmentManager.beginTransaction();
                fragmentTransaction.hide(thermSetFragment);
                fragmentTransaction.show(setFragment);
                fragmentTransaction.commit();
            }
        });
        return view;
    }
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if(!hidden){
            getTempPara();
            mThermAdapter.memberList.clear();
            mThermAdapter.memberList.add(new ThermSettingModel("Correction:", stFix,"OK"));
            mThermAdapter.memberList.add(new ThermSettingModel("Reflection:",stRefltmp,"OK"));
            mThermAdapter.memberList.add(new ThermSettingModel("Amb Temp:", stAirtmp,"OK"));
            mThermAdapter.memberList.add(new ThermSettingModel("Humidity:", stHumi,"OK"));
            mThermAdapter.memberList.add(new ThermSettingModel("Emissivity:", stEmiss,"OK"));
            mThermAdapter.memberList.add(new ThermSettingModel("Distance:", stDistance,"OK"));
            mThermAdapter.memberList.add(new ThermSettingModel("", "","Save"));
            mThermAdapter.notifyDataSetChanged();
        }
    }

    private class ThermSettingAdapter extends BaseAdapter implements View.OnClickListener{
        private Context context;
        private LayoutInflater layoutInflater;
        public List<ThermSettingModel> memberList;
        private ViewHolder viewHolder = null;


        public ThermSettingAdapter(Context context) {
            layoutInflater = LayoutInflater.from(context);
            memberList = new ArrayList<>();
            memberList.add(new ThermSettingModel("Correction:", stFix,"OK"));
            memberList.add(new ThermSettingModel("Reflection:",stRefltmp,"OK"));
            memberList.add(new ThermSettingModel("Amb Temp:", stAirtmp,"OK"));
            memberList.add(new ThermSettingModel("Humidity:", stHumi,"OK"));
            memberList.add(new ThermSettingModel("Emissivity:", stEmiss,"OK"));
            memberList.add(new ThermSettingModel("Distance:", stDistance,"OK"));
            memberList.add(new ThermSettingModel("", "","Save"));
            //       memberList.add(new SettingModel(R.drawable.language,R.string.language,R.drawable.setting_next_img));
            //       memberList.add(new SettingModel(R.drawable.language,R.string.offline_map,R.drawable.setting_next_img));
        }

        public int getCount() {
            return memberList.size();
        }

        @Override
        public Object getItem(int position) {
            return memberList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if(context == null)
                context = parent.getContext();
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.thermsetting_item, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.mTv = convertView.findViewById(R.id.tvThermtextView);
                viewHolder.mEt = convertView.findViewById(R.id.edThermInput);
                viewHolder.mBtn= convertView.findViewById(R.id.btnThermOk);
                convertView.setTag(viewHolder);
            }
            ThermSettingModel member = memberList.get(position);
            viewHolder = (ViewHolder)convertView.getTag();
            //设置tag标记
            viewHolder.mBtn.setTag(R.id.btnThermOk,position);//添加此代码
            viewHolder.mBtn.setText(member.getOk());
            viewHolder.mBtn.setOnClickListener(this);
            viewHolder.mTv.setText(member.getType());
            //设置tag标记
            viewHolder.mTv.setTag(R.id.tvThermtextView,position);//添加此代码
            viewHolder.mEt = convertView.findViewById(R.id.edThermInput);
            viewHolder.mEt.setText(member.getValue());
            if(position==6){
                viewHolder.mEt.setVisibility(View.INVISIBLE);
            }
            else{
                viewHolder.mEt.setVisibility(View.VISIBLE);
            }
            viewHolder.mEt.setInputType(InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);

            return convertView;
        }
        @Override
        public void onClick(View view) {
            int i = view.getId();
            if (i == R.id.btnThermOk) {
                int b = (int) view.getTag(R.id.btnThermOk);
                View parent = (View) view.getParent();
                EditText iput = parent.findViewById(R.id.edThermInput);
                String text = iput.getText().toString();
                if ("".equals(text)) {
                    ThermSettingModel member1 = memberList.get(b);
                    text = member1.getValue();
                    iput.setText(text);
                }
                if (b == 0 || b == 1 || b == 2 || b == 3 || b == 4) {
                    //0:correction   1:reflection    2:amb temp   3:humidity   4:emissivity
                    float fiput0 = Float.valueOf(text);
                    byte[] iput0 = new byte[4];
                    mByteUtil.putFloat(iput0, fiput0, 0);
                    mSendCommand.sendFloatCommand(b * 4, iput0[0], iput0[1], iput0[2], iput0[3], 20, 40, 60, 80, 120);
                } else if (b == 5) {
                    //5 distance
                    float fiput0 = Float.valueOf(text);
                    int intInput = (int) fiput0;
                    String stringInput = Integer.toString(intInput);
                    byte[] bIput0 = new byte[4];
                    mByteUtil.putInt(bIput0, intInput, 0);
                    // byte bIput0=Byte.valueOf(text);
                    mSendCommand.sendByteCommand(b * 4, bIput0[0], 20);
                    iput.setText(stringInput);
                } else if (b == 6) {
                    //6：save
                    mSendCommand.sendByteCommand(0x80, (byte) 0xff, 20);
                }


            }
        }

    }
    public class sendCommand {
        int psitionAndValue0=0, psitionAndValue1=0, psitionAndValue2=0, psitionAndValue3=0;

        public void sendFloatCommand(int position, byte value0, byte value1, byte value2, byte value3, int interval0, int interval1, int interval2, int interval3,int interval4) {
            psitionAndValue0 = (position << 8 )|(0x000000ff&value0);
            Handler handler0 = new Handler();
            handler0.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.setValue(UVCCamera.CTRL_ZOOM_ABS, psitionAndValue0);
                }
            }, interval0);

            psitionAndValue1 = ((position+1)<< 8 )| (0x000000ff&value1);
            handler0.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.setValue(UVCCamera.CTRL_ZOOM_ABS, psitionAndValue1);
                }
            }, interval1);
            psitionAndValue2 = ((position+2)<< 8)|(0x000000ff&value2);

            handler0.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.setValue(UVCCamera.CTRL_ZOOM_ABS, psitionAndValue2);
                }
            }, interval2);

            psitionAndValue3 = ((position+3)<< 8) |(0x000000ff&value3);

            handler0.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.setValue(UVCCamera.CTRL_ZOOM_ABS, psitionAndValue3);
                }
            }, interval3);

            handler0.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.whenShutRefresh();
                }
            }, interval4);


        }
        private void whenChangeTempPara() {
            if( mCameraHandler != null) {
                mCameraHandler.whenChangeTempPara();
            }
        }

        public void sendByteCommand(int position, byte value0, int interval0) {
            psitionAndValue0=(position<< 8) |(0x000000ff&value0);
            Handler handler0 = new Handler();
            handler0.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCameraHandler.setValue(UVCCamera.CTRL_ZOOM_ABS, psitionAndValue0);
                }
            }, interval0);
        }
    }
    static class ViewHolder{
        TextView mTv;
        EditText mEt;
        Button mBtn;
    }
}
