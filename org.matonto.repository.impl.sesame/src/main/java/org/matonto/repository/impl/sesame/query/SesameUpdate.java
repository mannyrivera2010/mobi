package org.matonto.repository.impl.sesame.query;

import org.matonto.query.api.Update;
import org.openrdf.query.UpdateExecutionException;

public class SesameUpdate extends SesameOperation implements Update {

    private org.openrdf.query.Update sesUpdate;

    public SesameUpdate(org.openrdf.query.Update sesUpdate) {
        super(sesUpdate);
        this.sesUpdate = sesUpdate;
    }


    @Override
    public void execute() throws UpdateExecutionException {
        try {
            sesUpdate.execute();
        } catch (org.openrdf.query.UpdateExecutionException e) {
            throw new UpdateExecutionException(e);
        }
    }

    public String toString() {
        return sesUpdate.toString();
    }
}
