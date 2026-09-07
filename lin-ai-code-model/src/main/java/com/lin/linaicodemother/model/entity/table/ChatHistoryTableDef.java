package com.lin.linaicodemother.model.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.table.TableDef;

import java.io.Serial;

/**
 * 对话历史 表定义层。
 *
 * @author Lin
 */
public class ChatHistoryTableDef extends TableDef {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对话历史
     */
    public static final ChatHistoryTableDef CHAT_HISTORY = new ChatHistoryTableDef();

    /**
     * id
     */
    public final QueryColumn ID = new QueryColumn(this, "id");

    /**
     * 应用id
     */
    public final QueryColumn APP_ID = new QueryColumn(this, "app_id");

    /**
     * 创建用户id
     */
    public final QueryColumn USER_ID = new QueryColumn(this, "user_id");

    /**
     * 消息
     */
    public final QueryColumn MESSAGE = new QueryColumn(this, "message");

    /**
     * 是否删除
     */
    public final QueryColumn IS_DELETE = new QueryColumn(this, "is_delete");

    /**
     * 创建时间
     */
    public final QueryColumn CREATE_TIME = new QueryColumn(this, "create_time");

    /**
     * 更新时间
     */
    public final QueryColumn UPDATE_TIME = new QueryColumn(this, "update_time");

    /**
     * user/ai
     */
    public final QueryColumn MESSAGE_TYPE = new QueryColumn(this, "message_type");

    /**
     * 所有字段。
     */
    public final QueryColumn ALL_COLUMNS = new QueryColumn(this, "*");

    /**
     * 默认字段，不包含逻辑删除或者 large 等字段。
     */
    public final QueryColumn[] DEFAULT_COLUMNS = new QueryColumn[]{ID, MESSAGE, MESSAGE_TYPE, APP_ID, USER_ID, CREATE_TIME, UPDATE_TIME, };

    public ChatHistoryTableDef() {
        super("", "chat_history");
    }

    private ChatHistoryTableDef(String schema, String name, String alisa) {
        super(schema, name, alisa);
    }

    public ChatHistoryTableDef as(String alias) {
        String key = getNameWithSchema() + "." + alias;
        return getCache(key, k -> new ChatHistoryTableDef("", "chat_history", alias));
    }

}
