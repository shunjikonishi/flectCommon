package jp.co.flect.javascript.jqgrid;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

import jp.co.flect.util.DateFormatHolder;
import jp.co.flect.log.Logger;
import jp.co.flect.log.LoggerFactory;

public class RdbQuery {
	
	private static final String DEFAULT_DATETIME_FORMAT = "yyyy/MM/dd HH:mm:ss.SSS";
	private static final String DEFAULT_DATE_FORMAT = "yyyy/MM/dd";
	private static final String DEFAULT_TIME_FORMAT = "HH:mm:ss.SSS";
	
	private static final Logger log = LoggerFactory.getLogger(RdbQuery.class);
	
	private PreparedStatement queryStmt;
	private PreparedStatement countStmt;
	private boolean useOffset;
	
	private DateFormatHolder formats = new DateFormatHolder();
	
	public RdbQuery(Connection con, String query) throws SQLException {
		this(con, query, null, false);
	}
	
	public RdbQuery(Connection con, String query, String countQuery) throws SQLException {
		this(con, query, countQuery, false);
	}
	
	public RdbQuery(Connection con, String query, String countQuery, boolean useOffset) throws SQLException {
		log.debug("RdbQuery: query: {0}", query);
		log.debug("RdbQuery: count: {0}", countQuery);
		log.debug("RdbQuery: useOffset: " + useOffset);
		
		this.useOffset = useOffset;
		if (useOffset) {
			query += " LIMIT ? OFFSET ?";
		}
		this.queryStmt = con.prepareStatement(query);
		if (countQuery != null) {
			try {
				this.countStmt = con.prepareStatement(countQuery);
			} catch (SQLException e) {
				try {
					this.queryStmt.close();
				} catch (SQLException e2) {
				}
				throw e;
			}
		}
	}
	
	public DateFormatHolder getDateFormatHolder() { return this.formats;}
	public void setDateFormatHolder(DateFormatHolder h) { this.formats = h;}
	
	public GridData getGridData(int page, int rowCount) throws SQLException {
		return getGridData(page, rowCount, null);
	}
	
	public GridData getGridData(int page, int rowCount, List<?> params) throws SQLException {
		int offset = (page - 1) * rowCount;
		int cnt = 0;
		if (this.countStmt != null) {
			if (params != null) {
				countStmt.clearParameters();
				int idx = 1;
				for (Object o : params) {
					setParameter(countStmt, idx++, o);
				}
			}
			ResultSet rs = countStmt.executeQuery();
			try {
				if (rs.next()) {
					cnt = rs.getInt(1);
				}
			} finally {
				rs.close();
			}
		}
		if (params != null || this.useOffset) {
			queryStmt.clearParameters();
			int idx = 1;
			if (params != null) {
				for (Object o : params) {
					setParameter(queryStmt, idx++, o);
				}
			}
			if (this.useOffset) {
				queryStmt.setInt(idx++, rowCount);
				queryStmt.setInt(idx++, offset);
			}
		}
		ResultSet rs = queryStmt.executeQuery();
		try {
			ResultSetMetaData meta = rs.getMetaData();
			int len = meta.getColumnCount();
			
			GridData result = new GridData(rowCount, page);
			while (rs.next()) {
				if (offset > 0 && !this.useOffset) {
					offset--;
					if (this.countStmt == null) {
						cnt++;
					}
					continue;
				}
				List<Object> list = new ArrayList<Object>();
				for (int i=0; i<len; i++) {
					list.add(getObject(rs, i+1, meta.getColumnType(i+1)));
				}
				result.addRow(list);
				if (this.countStmt == null) {
					cnt++;
				}
				if (result.getRows().size() >= rowCount) {
					break;
				}
			}
			if (this.countStmt == null) {
				while (rs.next()) {
					cnt++;
				}
			}
			result.setRecordCount(cnt);
			return result;
		} finally {
			rs.close();
		}
	}
	
	public void close() throws SQLException {
		SQLException ex = null;
		if (this.countStmt != null) {
			try {
				countStmt.close();
			} catch (SQLException e) {
				ex = e;
			}
		}
		this.queryStmt.close();
		if (ex != null) {
			throw ex;
		}
	}
	
	public static void setParameter(PreparedStatement stmt, int idx, Object o) throws SQLException {
		if (o == null) {
			throw new IllegalArgumentException("Not supported null");
		} else if (o instanceof String) {
			stmt.setString(idx, (String)o);
		} else if (o instanceof Integer) {
			stmt.setInt(idx, (Integer)o);
		} else if (o instanceof Boolean) {
			stmt.setBoolean(idx, (Boolean)o);
		} else if (o instanceof Timestamp) {
			stmt.setTimestamp(idx, (Timestamp)o);
		} else if (o instanceof Date) {
			stmt.setDate(idx, (Date)o);
		} else if (o instanceof byte[]) {
			stmt.setBytes(idx, (byte[])o);
		} else if (o instanceof Double) {
			stmt.setDouble(idx, (Double)o);
		} else if (o instanceof Long) {
			stmt.setLong(idx, (Long)o);
		} else if (o instanceof BigDecimal) {
			stmt.setBigDecimal(idx, (BigDecimal)o);
		} else if (o instanceof Float) {
			stmt.setFloat(idx, (Float)o);
		} else if (o instanceof Byte) {
			stmt.setByte(idx, (Byte)o);
		} else if (o instanceof Short) {
			stmt.setShort(idx, (Short)o);
		} else if (o instanceof Time) {
			stmt.setTime(idx, (Time)o);
		} else {
			throw new IllegalArgumentException("Not supported: " + o.getClass());
		}
	}
	
	public Object getObject(ResultSet rs, int idx, int type) throws SQLException {
		Object ret = null;
		switch (type) {
			case Types.ARRAY:
				ret = rs.getArray(idx);
				break;
			case Types.BIGINT:
			case Types.DECIMAL:
			case Types.NUMERIC:
				ret = rs.getBigDecimal(idx);
				break;
			case Types.BINARY:
			case Types.LONGVARBINARY:
			case Types.VARBINARY:
				ret = rs.getBytes(idx);
				break;
			case Types.BIT:
			case Types.BOOLEAN:
				ret = rs.getBoolean(idx);
				break;
			case Types.BLOB:
				ret = rs.getBlob(idx);
				break;
			case Types.CHAR:
			case Types.LONGNVARCHAR:
			case Types.LONGVARCHAR:
			case Types.NCHAR:
			case Types.NVARCHAR:
			case Types.VARCHAR:
			case Types.OTHER:
				ret = rs.getString(idx);
				break;
			case Types.CLOB:
				ret = rs.getClob(idx);
				break;
			case Types.NCLOB:
				ret = rs.getNClob(idx);
				break;
			case Types.DOUBLE:
			case Types.REAL:
				ret = rs.getDouble(idx);
				break;
			case Types.FLOAT:
				ret = rs.getFloat(idx);
				break;
			case Types.INTEGER:
			case Types.SMALLINT:
			case Types.TINYINT:
				ret = rs.getInt(idx);
				break;
			case Types.JAVA_OBJECT:
				ret = rs.getObject(idx);
				break;
			case Types.NULL:
				ret = null;
				break;
			case Types.REF:
				ret = rs.getRef(idx);
				break;
			case Types.ROWID:
				ret = rs.getRowId(idx);
				break;
			case Types.SQLXML:
				ret = rs.getSQLXML(idx);
				break;
			case Types.DATE:
				Date d = rs.getDate(idx);
				if (d != null) {
					if (this.formats.getTimeDifference() != 0) {
						d = new Date(d.getTime() + this.formats.getTimeDifference());
					}
					ret = this.formats.getDateFormat().format(d);
				}
				break;
			case Types.TIME:
				Time t = rs.getTime(idx);
				if (t != null) {
					if (this.formats.getTimeDifference() != 0) {
						t = new Time(t.getTime() + this.formats.getTimeDifference());
					}
					ret = this.formats.getTimeFormat().format(t);
				}
				break;
			case Types.TIMESTAMP:
				Timestamp ts = rs.getTimestamp(idx);
				if (ts != null) {
					if (this.formats.getTimeDifference() != 0) {
						ts = new Timestamp(ts.getTime() + this.formats.getTimeDifference());
					}
					ret = this.formats.getDatetimeFormat().format(ts);
				}
				break;
			case Types.DATALINK:
			case Types.DISTINCT:
			case Types.STRUCT:
			default:
				log.warn("Not supported: column=" + idx + ", type=" + type);
				ret = rs.getString(idx);
				break;
		}
		if (rs.wasNull()) {
			ret = null;
		}
		return ret;
 	}
}
