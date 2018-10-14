package com.suda.yzune.wakeupschedule.schedule

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.suda.yzune.wakeupschedule.AboutActivity
import com.suda.yzune.wakeupschedule.GlideApp
import com.suda.yzune.wakeupschedule.R
import com.suda.yzune.wakeupschedule.UpdateFragment
import com.suda.yzune.wakeupschedule.apply_info.ApplyInfoActivity
import com.suda.yzune.wakeupschedule.bean.TableBean
import com.suda.yzune.wakeupschedule.bean.TableSelectBean
import com.suda.yzune.wakeupschedule.bean.UpdateInfoBean
import com.suda.yzune.wakeupschedule.course_add.AddCourseActivity
import com.suda.yzune.wakeupschedule.intro.IntroActivity
import com.suda.yzune.wakeupschedule.schedule_import.LoginWebActivity
import com.suda.yzune.wakeupschedule.schedule_manage.ScheduleManageActivity
import com.suda.yzune.wakeupschedule.schedule_settings.ScheduleSettingsActivity
import com.suda.yzune.wakeupschedule.settings.SettingsActivity
import com.suda.yzune.wakeupschedule.utils.*
import com.suda.yzune.wakeupschedule.utils.CourseUtils.countWeek
import com.suda.yzune.wakeupschedule.utils.CourseUtils.isQQClientAvailable
import com.suda.yzune.wakeupschedule.utils.UpdateUtils.getVersionCode
import com.suda.yzune.wakeupschedule.widget.ModifyTableNameFragment
import es.dmoral.toasty.Toasty
import kotlinx.android.synthetic.main.activity_schedule.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.ParseException
import java.util.*


class ScheduleActivity : AppCompatActivity() {

    private lateinit var viewModel: ScheduleViewModel
    private var mAdapter: SchedulePagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        viewModel = ViewModelProviders.of(this).get(ScheduleViewModel::class.java)

        PreferenceUtils.init(applicationContext)
        ViewUtils.fullScreen(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        viewModel.updateFromOldVer()

        val fadeInAni = ObjectAnimator.ofFloat(vp_schedule, "alpha", 0f, 1f)
        fadeInAni.duration = 500
        viewModel.initViewData().observe(this, Observer { table ->
            if (table == null) return@Observer
            viewModel.initTimeData(table.timeTable)

            if (table.background != "") {
                val x = (ViewUtils.getRealSize(this).x * 0.5).toInt()
                val y = (ViewUtils.getRealSize(this).y * 0.5).toInt()
                GlideApp.with(this.applicationContext)
                        .load(table.background)
                        .override(x, y)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        //.apply(bitmapTransform(BlurTransformation(0, 5)))
                        .into(iv_bg)
            } else {
                val x = (ViewUtils.getRealSize(this).x * 0.5).toInt()
                val y = (ViewUtils.getRealSize(this).y * 0.5).toInt()
                GlideApp.with(this.applicationContext)
                        .load(R.drawable.main_background)
                        .override(x, y)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        //.apply(bitmapTransform(BlurTransformation(0, 5)))
                        .into(iv_bg)
            }

            for (i in 0 until cl_schedule.childCount) {
                val view = cl_schedule.getChildAt(i)
                when (view) {
                    is TextView -> view.setTextColor(table.textColor)
                    is ImageButton -> view.setColorFilter(table.textColor)
                }
            }

            viewModel.itemHeight = SizeUtils.dp2px(applicationContext, table.itemHeight.toFloat())
            viewModel.currentWeek.value = countWeek(table.startDate)
            initCourseData(table.id)
            sb_week.max = table.maxWeek - 1
            initViewPage(table.maxWeek, table)
            fadeInAni.start()

            ib_add.setOnClickListener {
                val intent = Intent(this, AddCourseActivity::class.java)
                intent.putExtra("tableId", table.id)
                intent.putExtra("maxWeek", table.maxWeek)
                intent.putExtra("id", -1)
                startActivity(intent)
            }

            ib_more.setOnClickListener { view ->
                val popupMenu = PopupMenu(this, view)
                popupMenu.menuInflater.inflate(R.menu.menu_more, popupMenu.menu)
                popupMenu.show()
                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.ib_settings -> {
                            val gson = Gson()
                            val intent = Intent(this, ScheduleSettingsActivity::class.java)
                            intent.putExtra("tableData", gson.toJson(table))
                            startActivity(intent)
                        }
                        R.id.ib_share -> {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                            } else {
                                ExportSettingsFragment().apply {
                                    this.isCancelable = false
                                }.show(supportFragmentManager, "exportSettingsFragment")
                            }
                        }
                        R.id.ib_manage -> {
                            startActivity(Intent(this, ScheduleManageActivity::class.java))
                        }
                    }
                    return@setOnMenuItemClickListener true
                }
            }

            viewModel.getScheduleWidgetIds().observe(this, Observer { list ->
                list?.forEach {
                    AppWidgetUtils.refreshScheduleWidget(this.applicationContext, appWidgetManager, it, table)
                }
            })
        })

        viewModel.currentWeek.observe(this, Observer {
            if (it == null) return@Observer
            viewModel.selectedWeek = it
            initEvent(it)
        })

        initNavView()

        val openTimes = PreferenceUtils.getIntFromSP(applicationContext, "open_times", 0)
        if (openTimes < 10) {
            PreferenceUtils.saveIntToSP(applicationContext, "open_times", openTimes + 1)
        } else if (openTimes == 10) {
            val dialog = DonateFragment.newInstance()
            dialog.isCancelable = false
            dialog.show(supportFragmentManager, "donateDialog")
            PreferenceUtils.saveIntToSP(applicationContext, "open_times", openTimes + 1)
        }

        if (!PreferenceUtils.getBooleanFromSP(applicationContext, "has_count", false)) {
            MyRetrofitUtils.instance.addCount(applicationContext)
        }

        if (PreferenceUtils.getBooleanFromSP(applicationContext, "s_update", true)) {
            MyRetrofitUtils.instance.getService().getUpdateInfo().enqueue(object : Callback<ResponseBody> {
                override fun onFailure(call: Call<ResponseBody>?, t: Throwable?) {}

                override fun onResponse(call: Call<ResponseBody>?, response: Response<ResponseBody>?) {
                    if (response!!.body() != null) {
                        val gson = Gson()
                        try {
                            val updateInfo = gson.fromJson<UpdateInfoBean>(response.body()!!.string(), object : TypeToken<UpdateInfoBean>() {
                            }.type)
                            if (updateInfo.id > getVersionCode(this@ScheduleActivity.applicationContext)) {
                                UpdateFragment.newInstance(updateInfo).show(supportFragmentManager, "updateDialog")
                            }
                        } catch (e: JsonSyntaxException) {

                        }
                    }
                }
            })
        }

        if (!PreferenceUtils.getBooleanFromSP(applicationContext, "has_intro", false)) {
            initIntro()
        }

        if (!PreferenceUtils.getBooleanFromSP(applicationContext, "v3.20", false)) {
            try {
                startActivity(Intent(this, IntroActivity::class.java))
            } catch (e: Exception) {
                Toasty.error(applicationContext, "使用教程载入失败>_<请劳烦自己探索").show()
            }
        }

        viewModel.initTableSelectList().observe(this, Observer {
            if (it == null) return@Observer
            viewModel.tableSelectList.clear()
            viewModel.tableSelectList.addAll(it)
            if (rv_table_name.adapter == null) {
                initTableMenu(viewModel.tableSelectList)
            } else {
                rv_table_name.adapter?.notifyDataSetChanged()
            }
        })
    }

    private fun initTableMenu(data: List<TableSelectBean>) {
        rv_table_name.layoutManager = LinearLayoutManager(this)
        val fadeOutAni = ObjectAnimator.ofFloat(vp_schedule, "alpha", 1f, 0f)
        fadeOutAni.duration = 500
        val adapter = TableNameAdapter(R.layout.item_table_select_main, data)
        adapter.addHeaderView(FrameLayout(this).apply {
            this.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SizeUtils.dp2px(this@ScheduleActivity.applicationContext, 24f))
        })
        adapter.addFooterView(initFooterView())
        adapter.setOnItemClickListener { _, _, position ->
            Log.d("位置", position.toString())
            if (position < data.size) {
                if (data[position].id != viewModel.tableData.value?.id) {
                    fadeOutAni.start()
                    viewModel.changeDefaultTable(data[position].id)
                }
            }
        }
        rv_table_name.adapter = adapter
    }

    private fun initFooterView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_table_add_main, rv_table_name, false)
        val tableAdd = view.findViewById<ImageButton>(R.id.nav_table_add)
        tableAdd.setOnClickListener {
            ModifyTableNameFragment.newInstance(object : ModifyTableNameFragment.TableNameChangeListener {
                override fun onFinish(editText: EditText, dialog: Dialog) {
                    if (!editText.text.toString().isEmpty()) {
                        viewModel.addBlankTable(editText.text.toString())
                        viewModel.addBlankTableInfo.observe(this@ScheduleActivity, Observer { info ->
                            if (info == "OK") {
                                Toasty.success(applicationContext, "新建成功~").show()
                                dialog.dismiss()
                            } else {
                                Toasty.success(applicationContext, "操作失败>_<").show()
                                dialog.dismiss()
                            }
                        })
                    } else {
                        Toasty.error(applicationContext, "名称不能为空哦>_<").show()
                    }
                }
            }).show(supportFragmentManager, "addTableFragment")
        }
        val tableManage = view.findViewById<ImageButton>(R.id.nav_table_manage)
        tableManage.setOnClickListener {
            startActivity(Intent(this, ScheduleManageActivity::class.java))
        }
        return view
    }

    private fun initCourseData(tableId: Int) {
        for (i in 1..7) {
            viewModel.getRawCourseByDay(i, tableId).observe(this, Observer { list ->
                viewModel.allCourseList[i - 1].value = list
            })
        }
    }

    private fun initIntro() {
        TapTargetSequence(this)
                .targets(
                        TapTarget.forView(ib_add, "这是手动添加课程的按钮", "新版本中添加课程变得友好很多哦，试试看\n点击白色区域告诉我你get到了")
                                .outerCircleColor(R.color.red)
                                .outerCircleAlpha(0.96f)
                                .targetCircleColor(R.color.white)
                                .titleTextSize(16)
                                .titleTextColor(R.color.white)
                                .descriptionTextSize(12)
                                .textColor(R.color.white)
                                .dimColor(R.color.black)
                                .drawShadow(true)
                                .cancelable(false)
                                .tintTarget(true)
                                .transparentTarget(false)
                                .targetRadius(60),
                        TapTarget.forView(ib_import, "这是导入课程的按钮", "现在已经支持采用正方教务系统的学校的课程自动导入了！\n还有别人分享给你的文件也要从这里导入哦~\n点击白色区域告诉我你get到了")
                                .outerCircleColor(R.color.lightBlue)
                                .outerCircleAlpha(0.96f)
                                .targetCircleColor(R.color.white)
                                .titleTextSize(16)
                                .titleTextColor(R.color.white)
                                .descriptionTextSize(12)
                                .textColor(R.color.white)
                                .dimColor(R.color.black)
                                .drawShadow(true)
                                .cancelable(false)
                                .tintTarget(true)
                                .transparentTarget(false)
                                .targetRadius(60),
                        TapTarget.forView(ib_more, "点这里发现更多", "比如可以分享课表给别人哦~\n多点去探索吧\n点击白色区域告诉我你get到了")
                                .outerCircleColor(R.color.blue)
                                .outerCircleAlpha(0.96f)
                                .targetCircleColor(R.color.white)
                                .titleTextSize(16)
                                .titleTextColor(R.color.white)
                                .descriptionTextSize(12)
                                .textColor(R.color.white)
                                .dimColor(R.color.black)
                                .drawShadow(true)
                                .cancelable(false)
                                .tintTarget(true)
                                .transparentTarget(false)
                                .targetRadius(60),
                        TapTarget.forView(tv_weekday, "点击此处可快速回到当前周", "主界面左右滑动可以切换周数\n点击这里就可以快速回到当前周啦\n点击白色区域告诉我你get到了")
                                .outerCircleColor(R.color.deepOrange)
                                .outerCircleAlpha(0.96f)
                                .targetCircleColor(R.color.white)
                                .titleTextSize(16)
                                .titleTextColor(R.color.white)
                                .descriptionTextSize(12)
                                .textColor(R.color.white)
                                .dimColor(R.color.black)
                                .drawShadow(true)
                                .cancelable(false)
                                .tintTarget(true)
                                .transparentTarget(false)
                                .targetRadius(60)
                ).listener(object : TapTargetSequence.Listener {
                    override fun onSequenceCanceled(lastTarget: TapTarget?) {

                    }

                    override fun onSequenceFinish() {
                        PreferenceUtils.saveBooleanToSP(this@ScheduleActivity.applicationContext, "has_intro", true)
                    }

                    override fun onSequenceStep(lastTarget: TapTarget?, targetClicked: Boolean) {
                    }

                }).start()
    }

    override fun onStart() {
        super.onStart()
        tv_date.text = CourseUtils.getTodayDate()
    }

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle?) {

    }

    private fun initNavView() {
        navigation_view.itemIconTintList = null
        navigation_view.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_setting -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    drawerLayout.postDelayed({
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }, 360)
                    return@setNavigationItemSelectedListener true
                }
                R.id.nav_explore -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    drawerLayout.postDelayed({
                        startActivity(Intent(this, ApplyInfoActivity::class.java))
                    }, 360)
                    return@setNavigationItemSelectedListener true
                }
                R.id.nav_help -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    drawerLayout.postDelayed({
                        CourseUtils.openUrl(this, "https://yzune.github.io/2018/08/13/WakeUp%E8%AF%BE%E7%A8%8B-%E9%97%AE%E7%AD%94-+-%E6%8A%80%E5%B7%A7/")
                    }, 360)
                    return@setNavigationItemSelectedListener true
                }
                R.id.nav_about -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    drawerLayout.postDelayed({
                        startActivity(Intent(this, AboutActivity::class.java))
                    }, 360)
                    return@setNavigationItemSelectedListener true
                }
                R.id.nav_young -> {
                    Toasty.info(this.applicationContext, "咩咩将为你记录倒计时等事件哦，敬请期待").show()
                    return@setNavigationItemSelectedListener true
                }
                R.id.nav_feedback -> {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    drawerLayout.postDelayed({
                        val c = Calendar.getInstance()
                        val hour = c.get(Calendar.HOUR_OF_DAY)
                        if (hour !in 8..21) {
                            Toasty.info(applicationContext, "开发者在休息哦(～﹃～)~zZ请换个时间反馈吧").show()
                        } else {
                            if (isQQClientAvailable(applicationContext)) {
                                val qqUrl = "mqqwpa://im/chat?chat_type=wpa&uin=1055614742&version=1"
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(qqUrl)))
                            } else {
                                Toasty.error(applicationContext, "手机上没有安装QQ，无法启动聊天窗口:-(", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, 360)
                    return@setNavigationItemSelectedListener true
                }
                else -> {
                    Toasty.info(this.applicationContext, "敬请期待").show()
                    return@setNavigationItemSelectedListener true
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ExportSettingsFragment().apply {
                        this.isCancelable = false
                    }.show(supportFragmentManager, "exportSettingsFragment")
                } else {
                    Toasty.error(applicationContext, "你取消了授权>_<无法导出", Toast.LENGTH_LONG).show()
                }
            }
            2 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(this, LoginWebActivity::class.java)
                    intent.putExtra("type", "file")
                    startActivity(intent)
                } else {
                    Toasty.error(applicationContext, "你取消了授权>_<无法从文件导入", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun initViewPage(maxWeek: Int, table: TableBean) {
        if (mAdapter == null) {
            mAdapter = SchedulePagerAdapter(supportFragmentManager)
            vp_schedule.adapter = mAdapter
            vp_schedule.offscreenPageLimit = 1
        }
        mAdapter!!.removeAll()
        for (i in 1..maxWeek) {
            mAdapter!!.addFragment(ScheduleFragment.newInstance(i))
        }
        mAdapter!!.notifyDataSetChanged()
        if (CourseUtils.countWeek(table.startDate) > 0) {
            vp_schedule.currentItem = CourseUtils.countWeek(table.startDate) - 1
        } else {
            vp_schedule.currentItem = 0
        }
    }

    private fun initEvent(currentWeek: Int) {
        sb_week.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                try {
                    if (currentWeek > 0) {
                        if (progress + 1 == currentWeek) {
                            tv_week.text = "第${progress + 1}周"
                            tv_weekday.text = CourseUtils.getWeekday()
                        } else {
                            tv_week.text = "第${progress + 1}周"
                            tv_weekday.text = "非本周"
                        }
                    } else {
                        tv_week.text = "还没有开学哦"
                        tv_weekday.text = CourseUtils.getWeekday()
                    }
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                vp_schedule.currentItem = seekBar!!.progress
            }
        })

        ib_nav.setOnClickListener { drawerLayout.openDrawer(Gravity.START) }

        ib_import.setOnClickListener {
            ImportChooseFragment.newInstance().show(supportFragmentManager, "importDialog")
        }

        tv_weekday.setOnClickListener {
            tv_weekday.text = CourseUtils.getWeekday()
            if (currentWeek > 0) {
                vp_schedule.currentItem = currentWeek - 1
            } else {
                vp_schedule.currentItem = 0
            }
        }

        vp_schedule.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

            override fun onPageSelected(position: Int) {
                viewModel.selectedWeek = position + 1
                sb_week.progress = position
                try {
                    if (currentWeek > 0) {
                        if (viewModel.selectedWeek == currentWeek) {
                            tv_week.text = "第${viewModel.selectedWeek}周"
                            tv_weekday.text = CourseUtils.getWeekday()
                        } else {
                            tv_week.text = "第${viewModel.selectedWeek}周"
                            tv_weekday.text = "非本周"
                        }
                    } else {
                        tv_week.text = "还没有开学哦"
                        tv_weekday.text = CourseUtils.getWeekday()
                    }
                } catch (e: ParseException) {
                    e.printStackTrace()
                }

            }

            override fun onPageScrolled(a: Int, b: Float, c: Int) {

            }

            override fun onPageScrollStateChanged(state: Int) {

            }
        })
    }
}