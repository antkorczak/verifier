/*
 * This project is licensed as below.
 *
 * **************************************************************************
 *
 * Copyright 2020-2022 Intel Corporation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * **************************************************************************
 *
 */

package com.intel.bkp.verifier.command.responses.attestation;

import com.intel.bkp.fpgacerts.dice.tcbinfo.FwIdField;
import com.intel.bkp.fpgacerts.dice.tcbinfo.FwidHashAlg;
import com.intel.bkp.fpgacerts.dice.tcbinfo.TcbInfo;
import com.intel.bkp.fpgacerts.dice.tcbinfo.TcbInfoField;
import com.intel.bkp.fpgacerts.exceptions.FwidHashAlgNotSupported;
import com.intel.bkp.utils.ByteBufferSafe;
import com.intel.bkp.verifier.exceptions.VerifierRuntimeException;
import com.intel.bkp.verifier.model.evidence.SectionType;

import java.util.EnumMap;
import java.util.Map;

import static com.intel.bkp.fpgacerts.dice.tcbinfo.TcbInfoConstants.VENDOR;
import static com.intel.bkp.fpgacerts.model.Oid.MEASUREMENT_TYPES;

public abstract class BaseMeasurementRecordToTcbInfoMapper<T> {

    private static final int LAYER = 2;

    public abstract TcbInfo map(T header, ByteBufferSafe recordContentBuffer);

    protected abstract int getPrSectionIndex(T header);

    protected abstract int getMeasurementSize(T header);

    protected TcbInfo mapInternal(T header, ByteBufferSafe recordContentBuffer, SectionType sectionType) {
        final Map<TcbInfoField, Object> map = createBaseMap(sectionType);

        if (SectionType.PR == sectionType) {
            map.put(TcbInfoField.INDEX, getPrSectionIndex(header));
        }

        if (SectionType.DEVICE_STATE == sectionType) {
            map.put(TcbInfoField.VENDOR_INFO, new DeviceStateMeasurementRecord(recordContentBuffer).getData());
        } else {
            map.put(TcbInfoField.FWIDS, new FwIdField(getFwidHashAlg(header).getOid(),
                new UserDesignMeasurementRecord(recordContentBuffer).getData())
            );
        }

        return new TcbInfo(map);
    }

    private FwidHashAlg getFwidHashAlg(T header) {
        try {
            return FwidHashAlg.from(getMeasurementSize(header));
        } catch (FwidHashAlgNotSupported e) {
            throw new VerifierRuntimeException("Parsing FwIdHashALg failed.", e);
        }
    }

    protected Map<TcbInfoField, Object> createBaseMap(SectionType sectionType) {
        return createBaseMap(getMeasurementOid(sectionType));
    }

    private Map<TcbInfoField, Object> createBaseMap(String type) {
        final var map = new EnumMap<>(TcbInfoField.class);
        map.put(TcbInfoField.VENDOR, VENDOR);
        map.put(TcbInfoField.TYPE, type);
        map.put(TcbInfoField.LAYER, LAYER);
        return map;
    }

    private String getMeasurementOid(final SectionType sectionType) {
        return String.format("%s.%d", MEASUREMENT_TYPES.getOid(), sectionType.getValue());
    }
}