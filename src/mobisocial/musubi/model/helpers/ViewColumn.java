/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.model.helpers;

public class ViewColumn {
    private final String tableColumn;
    private final String viewColumn;
    private final String tableName;

    public ViewColumn(String commonColumnName, String tableName) {
        this.viewColumn = commonColumnName;
        this.tableColumn = commonColumnName;
        this.tableName = tableName;
    }

    public ViewColumn(String viewColumn, String tableName, String tableColumn) {
        this.viewColumn = viewColumn;
        this.tableColumn = tableColumn;
        this.tableName = tableName;
    }

    /**
     * eg my_table.its_col
     */
    public String getTableColumn() {
        return tableName + "." + tableColumn;
    }

    /**
     * eg my_view_col
     */
    public String getViewColumn() {
        return viewColumn;
    }
}