package com.webank.wedatasphere.linkis.engineplugin.hive.hook

import com.webank.wedatasphere.linkis.common.utils.{Logging, Utils}
import com.webank.wedatasphere.linkis.engineconn.common.creation.EngineCreationContext
import com.webank.wedatasphere.linkis.engineconn.computation.executor.execute.EngineExecutionContext
import com.webank.wedatasphere.linkis.engineconn.computation.executor.hook.ComputationExecutorHook
import com.webank.wedatasphere.linkis.engineplugin.hive.exception.HiveQueryFailedException
import org.apache.commons.lang.StringUtils
import org.apache.hadoop.hive.conf.HiveConf.ConfVars

import java.util
import java.util.regex.Pattern
import scala.collection.JavaConverters.mapAsScalaMapConverter

/**
 * @author alexyang
 * @date 2021/6/25
 * @description
 */
class HiveAddMetaTableNameHook extends ComputationExecutorHook with Logging {

  private val HIVE_USE_TABLENAME_REGEX = ConfVars.HIVE_RESULTSET_USE_UNIQUE_COLUMN_NAMES.varname + "\\s*=\\s*(true|false)"

  override def getHookName(): String = "Add tableName config in metadata of hive result."

  override def beforeExecutorExecute(engineExecutionContext: EngineExecutionContext, engineCreationContext: EngineCreationContext, codeBeforeHook: String): String = {
    val configMap = new util.HashMap[String, String]()
    engineCreationContext.getOptions.asScala.filterNot(_._2.isInstanceOf[util.Map[String, Any]]).foreach(kv => configMap.put(kv._1, s"${kv._2}"))
    engineExecutionContext.getProperties.asScala.filterNot(_._2.isInstanceOf[util.Map[String, Any]]).foreach(kv => configMap.put(kv._1, s"${kv._2}"))
    if (configMap.containsKey(ConfVars.HIVE_RESULTSET_USE_UNIQUE_COLUMN_NAMES.varname)) {
      engineExecutionContext.setEnableResultsetMetaWithTableName(configMap.get(ConfVars.HIVE_RESULTSET_USE_UNIQUE_COLUMN_NAMES.varname).toBoolean)
    }

    if (codeBeforeHook.contains(ConfVars.HIVE_RESULTSET_USE_UNIQUE_COLUMN_NAMES.varname)) {
      val pattern = Pattern.compile(HIVE_USE_TABLENAME_REGEX)
      codeBeforeHook.split("\n").foreach(line => {
        if (StringUtils.isNotBlank(line)) {
          val mather = pattern.matcher(line)
          if (mather.find()) {
            val value = mather.group(1)
            Utils.tryCatch {
              val boolValue = value.toBoolean
              if (engineExecutionContext.getProperties.containsKey(ConfVars.HIVE_RESULTSET_USE_UNIQUE_COLUMN_NAMES.varname)) {
                warn(s"Should not add param ${mather.group()} in both code and starupMap, will use the param in code.")
              }
              engineExecutionContext.setEnableResultsetMetaWithTableName(boolValue)
            } {
              case e: IllegalArgumentException =>
                throw HiveQueryFailedException(41006, s"Invalid value : ${value} in param [${mather.group()}]")
            }
          }
        }
      })
    }
    codeBeforeHook
  }
}
