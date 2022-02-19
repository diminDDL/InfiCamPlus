package com.infiRay.XthermDemo;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.serenegiant.usbcameracommon.UVCCameraHandler;

import java.util.ArrayList;
import java.util.List;

import static java.sql.Types.NULL;

/**
 * Created by Administrator on 2018/1/29 0029.
 */

public class SettingFragment extends Fragment {
    private UVCCameraHandler mCameraHandler;
    private ListView mLvMember;
    private ImageButton mIbBack;
    private SettingAdapter mAdapter;
    private Fragment mThermometrySetting=null,mImageSetting=null;
    public boolean isOnSetting;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isOnSetting=true;
        //initImageLoader();
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.setting_fragment, container,
                false);
        mLvMember = (ListView)view.findViewById(R.id.lvSettingMember);
        mIbBack=(ImageButton)view.findViewById(R.id.setting_back);
        if(null == mAdapter){
            mAdapter = new SettingAdapter(getActivity());
        }
        mLvMember.setAdapter(mAdapter);
        mIbBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isOnSetting=false;

                MainActivity main=(MainActivity)getActivity();

                FragmentManager fragmentManager=getFragmentManager();
                Fragment fragment=fragmentManager.findFragmentByTag("setting");
                FragmentTransaction fragmentTransaction=fragmentManager.beginTransaction();
                fragmentTransaction.hide(fragment);
                fragmentTransaction.commit();
            }
        });

        mLvMember.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                switch (position){ //mThermometrySetting
                    case 0:
                        if(mThermometrySetting==null) {
                            mThermometrySetting = new ThermometrySetting();
                            fragmentTransaction.add(R.id.content_layout, mThermometrySetting, "ThermometrySetting");
                        }
                            Fragment fragment=fragmentManager.findFragmentByTag("setting");
                            fragmentTransaction.hide(fragment);
                            fragmentTransaction.show(mThermometrySetting);
                            fragmentTransaction.commit();
                        //				getFragmentManager().beginTransaction()
                        //						.replace(R.id.content_layout, settingFragment).commit();
                        //				getFragmentManager().beginTransaction().show(settingFragment);


                        break;

                    case 1: //语言
             //           startActivity(new Intent(getActivity(),LanguageChange.class));
                        break;

                }
            }
        });

        return view;
    }
        public boolean getIsOnSetting(){
            return isOnSetting;
        }
    public void setIsOnSetting(boolean isSetting){
         isOnSetting=isSetting;
    }
    private class SettingAdapter extends BaseAdapter {
        private LayoutInflater layoutInflater;
        private List<SettingModel> memberList;

        public SettingAdapter(Context context){
            layoutInflater = LayoutInflater.from(context);
            memberList=new ArrayList<>();
           memberList.add(new SettingModel(NULL,R.string.temp_setting,R.mipmap.setting_next_img));
            memberList.add(new SettingModel(NULL,R.string.image_setting,R.mipmap.setting_next_img));

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
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.setting_item, parent, false);
            }

            SettingModel member = memberList.get(position);
            ImageView ivImage = (ImageView) convertView
                    .findViewById(R.id.ivSetImage);
            if(member.getImage()!=NULL) {
                ivImage.setImageResource(member.getImage());
            }

            TextView tvName = (TextView) convertView
                    .findViewById(R.id.tvSet);
            tvName.setText(member.getTitle());

            ImageView setimageView = (ImageView) convertView.findViewById(R.id.ivSetNext);
            setimageView.setImageResource(member.getSetimage());
            return convertView;
        }
    }
}
