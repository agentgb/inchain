package org.inchain.wallet.controllers;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.inchain.core.AccountKeyValue;
import org.inchain.kit.InchainInstance;
import org.inchain.store.AccountStore;
import org.inchain.utils.DateUtil;
import org.inchain.wallet.entity.BusinessEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.util.Callback;

/**
 * 商家列表页面控制器
 * @author ln
 *
 */
public class BusinessRecordController implements SubPageController {
	
	private static final Logger log = LoggerFactory.getLogger(BusinessRecordController.class);
	
	public TableView<BusinessEntity> table;
	
	public TableColumn<BusinessEntity, Integer> status;
	public TableColumn<BusinessEntity, byte[]> logo;
	public TableColumn<BusinessEntity, String> name;
	public TableColumn<BusinessEntity, List<AccountKeyValue>> detail;
	public TableColumn<BusinessEntity, String> time;
	
	private List<AccountStore> businessList;
	
	/**
	 *  FXMLLoader 调用的初始化
	 */
    public void initialize() {
    	//status.setCellValueFactory(new PropertyValueFactory<BusinessEntity, Integer>("status"));
    	logo.setCellValueFactory(new PropertyValueFactory<BusinessEntity, byte[]>("logo"));
    	logo.setCellFactory(new Callback<TableColumn<BusinessEntity, byte[]>, TableCell<BusinessEntity, byte[]>>() {
    		@Override 
    		public TableCell<BusinessEntity, byte[]> call(TableColumn<BusinessEntity, byte[]> tableColumn) {
    			return new TableCell<BusinessEntity, byte[]>() {
    				@Override
    				protected void updateItem(byte[] item, boolean empty) {
    					super.updateItem(item, empty);
    					if(item == null) {
    						setGraphic(null);
    					} else {
//    						ImageView imageView = new ImageView(new Image(new ByteArrayInputStream(item)));
    						ImageView imageView = new ImageView(new String(item));
    						imageView.setFitWidth(30);
    						imageView.setFitHeight(30);
    						setGraphic(imageView);
    					}
    				}
    			};
    		}
    	});
    	name.setCellValueFactory(new PropertyValueFactory<BusinessEntity, String>("name"));
    	detail.setCellValueFactory(new PropertyValueFactory<BusinessEntity, List<AccountKeyValue>>("details"));
    	detail.setCellFactory(new Callback<TableColumn<BusinessEntity, List<AccountKeyValue>>, TableCell<BusinessEntity, List<AccountKeyValue>>>() {
	    	@Override 
	    	public TableCell<BusinessEntity, List<AccountKeyValue>> call(TableColumn<BusinessEntity, List<AccountKeyValue>> tableColumn) {
	    		return new TableCell<BusinessEntity,List<AccountKeyValue>>() {
    				@Override
    				protected void updateItem(List<AccountKeyValue> items, boolean empty) {
    					setTextOverrun(OverrunStyle.CENTER_WORD_ELLIPSIS);
    					
    					super.updateItem(items, empty);
    					if(items == null || items.size() == 0) {
    						setGraphic(null);
    					} else {
    						VBox box = new VBox(5);
    						box.setPrefHeight(110);
    						Insets padding= new Insets(10,10,10,10);
							box.setPadding(padding);
    						for (AccountKeyValue keyValuePair : items) {
    							String name = keyValuePair.getName();
    							Label nameLabel = new Label(name);
    							nameLabel.setEllipsisString(name.substring(0, name.length() > 4 ? 4 : name.length()));
    							nameLabel.setWrapText(true);
    							
    							HBox hbox = new HBox(3);
    							hbox.getChildren().add(nameLabel);

    							Label splitLabel = new Label(" : ");
    							splitLabel.setEllipsisString(" : ");
    							splitLabel.setWrapText(true);
    							hbox.getChildren().add(splitLabel);
    							
    							String value = keyValuePair.getValueToString();
    							
    							Label valueLabel = new Label(value);
    							Tooltip tooltip = new Tooltip(name+" : "+value);
    							tooltip.setFont(Font.font(14));
    							tooltip.setMaxWidth(480);
    							tooltip.setWrapText(true);
    							valueLabel.setTooltip(tooltip);
    							valueLabel.setEllipsisString(value.substring(0, value.length() > 10 ? 10 : value.length()));
    							valueLabel.setWrapText(true);
    							valueLabel.setPrefHeight(-1);
    							
    							hbox.getChildren().add(valueLabel);
    							
    							box.getChildren().add(hbox);
							}
    						
    						setGraphic(box);
    					}
    				}
    			};
	    	}
	    });
    	time.setCellValueFactory(new PropertyValueFactory<BusinessEntity, String>("time") {
    		@Override
    		public ObservableValue<String> call(CellDataFeatures<BusinessEntity, String> param) {
    			return new ReadOnlyObjectWrapper<String>(DateUtil.convertDate(new Date(param.getValue().getTime()*1000)));
    		}
    	});
    }
    
    /**
     * 初始化
     */
    public void initDatas() {
    	
    	if(log.isDebugEnabled()) {
    		log.debug("加载商家列表···");
    	}
    	
    	businessList = InchainInstance.getInstance().getAccountKit().getCertAccounts(businessList);
    	
    	List<BusinessEntity> list = tx2Entity();
    	
    	ObservableList<BusinessEntity> datas = FXCollections.observableArrayList(list);
    	datas.sort(new Comparator<BusinessEntity>() {
			@Override
			public int compare(BusinessEntity o1, BusinessEntity o2) {
				long v1 = o1.getTime();
				long v2 = o2.getTime();
				if(v1 == v2) {
					return 0;
				} else if(v1 > v2) {
					return -1;
				} else {
					return 1;
				}
			}
		});
    	
    	table.setItems(datas);
    }

	private List<BusinessEntity> tx2Entity() {
		
		List<BusinessEntity> bes = new ArrayList<BusinessEntity>();
		
		if(businessList == null || businessList.size() == 0) {
			return bes;
		}
		
		for (AccountStore account : businessList) {
			//认证账户注册
			List<AccountKeyValue> bodyContents = account.getAccountBody().getContents();
			if(bodyContents == null)
				continue;
			BusinessEntity entity = new BusinessEntity();
			entity.setTime(account.getCreateTime());
			
			for (AccountKeyValue keyValuePair : bodyContents) {
				if(AccountKeyValue.LOGO.getCode().equals(keyValuePair.getCode())) {
					//图标
					entity.setLogo(keyValuePair.getValue());
				} else if(AccountKeyValue.NAME.getCode().equals(keyValuePair.getCode())) {
					//图标
					entity.setName(keyValuePair.getValueToString());
				} else {
					entity.addDetail(keyValuePair);
				}
			}
			
			bes.add(entity);
		}
		
		return bes;
	}
	
	@Override
	public void onShow() {
		initDatas();
	}

	@Override
	public void onHide() {
		
	}

	@Override
	public boolean refreshData() {
		return true;
	}

	@Override
	public boolean startupInit() {
		return false;
	}
}
