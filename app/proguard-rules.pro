# 小工具，release 不开混淆。此处保留默认规则。
# 若后续开启 minify，注意保留 Service（exported，反射/Intent 启动）。
-keep class com.helper.captchaalarm.AlarmService { *; }
