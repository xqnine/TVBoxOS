package com.github.tvbox.osc.ui.dialog.util

import android.content.Context
import android.text.TextUtils
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import com.github.tvbox.osc.bean.MoreSourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ext.findFirst
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KVStorage
import com.lzy.okgo.OkGo
import com.lzy.okgo.cache.CacheMode
import com.lzy.okgo.callback.StringCallback
import com.lzy.okgo.model.Response
import com.orhanobut.hawk.Hawk
import org.greenrobot.eventbus.EventBus
import org.json.JSONArray
import org.json.JSONObject

class SourceLineDialogUtil(private val context: Context) {

    private var DEFAULT_URL = "https://gitea.com/apkcore/apk_release/raw/branch/main/tv/update_yuan"
    private val dialog by lazy {
        SelectDialog<MoreSourceBean>(context)
    }

    init {
        val defaultBean =
            KVStorage.getBean(HawkConfig.CUSTOM_STORE_HOUSE_SELECTED, MoreSourceBean::class.java)
        if (defaultBean != null) {
            DEFAULT_URL = defaultBean.sourceUrl
        }
    }

    fun getData(onSelect: () -> Unit) {
        OkGo.get<String>(DEFAULT_URL).cacheMode(CacheMode.IF_NONE_CACHE_REQUEST)
            .cacheTime(24 * 60 * 60 * 1000).execute(object : StringCallback() {
                override fun onSuccess(response: Response<String>?) {
                    inflateData(response, onSelect)
                }

                override fun onCacheSuccess(response: Response<String>?) {
                    super.onCacheSuccess(response)
                    inflateData(response, onSelect)
                }

                override fun onError(response: Response<String>?) {
                    super.onError(response)
                    Toast.makeText(
                        context,
                        "接口请求失败",
                        Toast.LENGTH_LONG
                    ).show()
                }

            })
    }

    private fun inflateData(response: Response<String>?, onSelect: () -> Unit) {
        try {
            val json = JSONObject(response?.body().toString())
            val urls: JSONArray = json.getJSONArray("urls")
            //暂时不开放vip，因为有18+，不适合家庭使用
            if (json.has("vip")) {
                val vips = json.getJSONArray("vip")
            }
            val length = urls.length();
            val data = mutableListOf<MoreSourceBean>()
            for (i in 0 until length) {
                val jsonObj = urls.getJSONObject(i)
                val moreSourceBean = MoreSourceBean().apply {
                    sourceUrl = jsonObj.getString("url")
                    sourceName = jsonObj.getString("name")
                    isServer = true
                }
                data.add(moreSourceBean)
            }
            val selectUrl = Hawk.get(HawkConfig.API_URL, "")
            val findData = data.findFirst {
                it.sourceUrl == selectUrl
            }
            var select = 0
            findData?.let {
                select = data.indexOf(findData)
            }
            showDialog(data, select, onSelect = onSelect)
        } catch (e: Exception) {
            Toast.makeText(context, "解析地址失败，检查你的仓库地址是否正确", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDialog(list: List<MoreSourceBean>, select: Int, onSelect: () -> Unit) {

        dialog.apply {
            setTip("选择线路")
            setAdapter(object : SelectDialogAdapter.SelectDialogInterface<MoreSourceBean> {
                override fun click(moreSourceBea: MoreSourceBean?, pos: Int) {
                    //更新源
                    Hawk.put(HawkConfig.API_URL, moreSourceBea?.sourceUrl)
                    Hawk.put(HawkConfig.API_URL_BEAN, moreSourceBea)
                    EventBus.getDefault().post(
                        RefreshEvent(
                            RefreshEvent.TYPE_API_URL_CHANGE,
                            moreSourceBea?.sourceName?.ifEmpty { moreSourceBea.sourceUrl }
                        )
                    )
                    dialog.dismiss()
                    onSelect.invoke()
                }

                override fun getDisplay(moreSourceBea: MoreSourceBean?): String {
                    return if (moreSourceBea?.sourceName.isNullOrEmpty()) moreSourceBea?.sourceUrl
                        ?: "" else moreSourceBea?.sourceName ?: ""
                }

            }, object : DiffUtil.ItemCallback<MoreSourceBean>() {
                override fun areItemsTheSame(
                    oldItem: MoreSourceBean,
                    newItem: MoreSourceBean
                ): Boolean {
                    return TextUtils.equals(oldItem.sourceUrl, newItem.sourceUrl)
                }

                override fun areContentsTheSame(
                    oldItem: MoreSourceBean,
                    newItem: MoreSourceBean
                ): Boolean {
                    return TextUtils.equals(oldItem.sourceUrl, newItem.sourceUrl)
                }

            }, list, select)
        }

        dialog.show()
    }


}