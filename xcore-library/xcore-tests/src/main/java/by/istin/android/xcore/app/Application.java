package by.istin.android.xcore.app;

import by.istin.android.xcore.CoreApplication;
import by.istin.android.xcore.issues.issue12.processor.DaysBatchProcessor;
import by.istin.android.xcore.processor.SimpleEntityBatchProcessor;
import by.istin.android.xcore.processor.SimpleEntityProcessor;
import by.istin.android.xcore.processor.SimpleEntityWithPrimitiveConverterBatchProcessor;
import by.istin.android.xcore.processor.SimpleEntityWithPrimitiveEntityBatchProcessor;
import by.istin.android.xcore.processor.SimpleEntityWithSubEntitiesBatchProcessor;
import by.istin.android.xcore.processor.SimpleEntityWithSubEntityBatchProcessor;
import by.istin.android.xcore.processor.SimpleEntityWithSubJsonBatchProcessor;
import by.istin.android.xcore.provider.ContentProvider;
import by.istin.android.xcore.provider.IDBContentProviderSupport;

/**
 * Created by Uladzimir_Klyshevich on 12/6/13.
 */
public class Application extends CoreApplication {

    @Override
    public void onCreate() {
        super.onCreate();

        IDBContentProviderSupport defaultDBContentProvider = getDefaultDBContentProvider(ContentProvider.ENTITIES);

        registerAppService(new SimpleEntityProcessor());
        registerAppService(new SimpleEntityBatchProcessor(defaultDBContentProvider));
        registerAppService(new SimpleEntityWithSubEntityBatchProcessor(defaultDBContentProvider));
        registerAppService(new SimpleEntityWithSubJsonBatchProcessor(defaultDBContentProvider));
        registerAppService(new SimpleEntityWithPrimitiveEntityBatchProcessor(defaultDBContentProvider));
        registerAppService(new SimpleEntityWithPrimitiveConverterBatchProcessor(defaultDBContentProvider));
        registerAppService(new SimpleEntityWithSubEntitiesBatchProcessor(defaultDBContentProvider));

        //issue 12
        registerAppService(new DaysBatchProcessor(defaultDBContentProvider));
    }
}
