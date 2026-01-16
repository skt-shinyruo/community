package com.nowcoder.community.entity;

/**
 * 封装分页相关的信息
 *
 */
public class Page {

    // current page index
    private int current = 1;
    // single page discuss post number limit
    private int limit = 10;
    // total rows (# discuss post)
    private int rows;
    // url
    private String path;

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        if(current > 1){
            this.current = current;
        }
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        if (limit >= 1 && limit <= 100) {
            this.limit = limit;
        }
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        if (rows >= 0) {
            this.rows = rows;
        }
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "Page{" +
                "current=" + current +
                ", limit=" + limit +
                ", rows=" + rows +
                ", path='" + path + '\'' +
                '}';
    }

    /**
     * get current page start row
     * @return
     */
    public int getOffset(){
        // current * limit - limit
        return (current - 1) * limit;
    }

    /**
     * get total page number
     * @return
     */
    public int getTotal() {
        if (rows % limit == 0) {
            return rows / limit;
        }else {
            return rows / limit + 1;
        }
    }

    public int getFrom() {
        int from = current - 2;
        return Math.max(from, 1);
    }

    public int getTo() {
        int to = current + 2;
        int total = getTotal();
        return Math.min(to, total);
    }
}
